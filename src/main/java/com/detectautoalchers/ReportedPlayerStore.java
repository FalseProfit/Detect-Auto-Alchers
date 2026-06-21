package com.detectautoalchers;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import net.runelite.client.RuneLite;

final class ReportedPlayerStore
{
    private static final String DATE_REPORTED_COLUMN = "date_reported";
    private static final String DATE_WATCHED_COLUMN = "date_watched";
    private static final String DATE_ALLOWLISTED_COLUMN = "date_allowlisted";
    private static final String DIRECTORY = "detect-auto-alchers";
    private static final String REPORTED_FILE_NAME = "reported-players.csv";
    private static final String WATCHLIST_FILE_NAME = "watchlist.csv";
    private static final String OVERRIDE_LIST_FILE_NAME = "override-list.csv";
    private static final ConcurrentMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private final Path path;
    private final String dateColumnName;
    private final Map<String, ReportedPlayer> reportedPlayers = new LinkedHashMap<>();

    ReportedPlayerStore(Path path)
    {
        this(path, DATE_REPORTED_COLUMN);
    }

    ReportedPlayerStore(Path path, String dateColumnName)
    {
        this.path = path;
        this.dateColumnName = dateColumnName;
    }

    static ReportedPlayerStore createDefault()
    {
        return create(REPORTED_FILE_NAME, DATE_REPORTED_COLUMN);
    }

    static ReportedPlayerStore createWatchlist()
    {
        return create(WATCHLIST_FILE_NAME, DATE_WATCHED_COLUMN);
    }

    static ReportedPlayerStore createOverrideList()
    {
        return create(OVERRIDE_LIST_FILE_NAME, DATE_ALLOWLISTED_COLUMN);
    }

    private static ReportedPlayerStore create(String fileName, String dateColumnName)
    {
        return new ReportedPlayerStore(
            RuneLite.RUNELITE_DIR.toPath().resolve(DIRECTORY).resolve(fileName),
            dateColumnName
        );
    }

    synchronized void load() throws IOException
    {
        replaceReportedPlayers(withStoreLock(() -> read(path, dateColumnName)));
    }

    synchronized void record(String normalizedName, String displayName, Instant dateReported) throws IOException
    {
        String normalized = DetectorService.normalizeName(normalizedName);
        if (normalized.isEmpty())
        {
            return;
        }

        String display = displayName == null || displayName.trim().isEmpty() ? normalized : displayName.trim();
        if (hasSpreadsheetFormulaPrefix(normalized) || hasSpreadsheetFormulaPrefix(display))
        {
            return;
        }

        withStoreLock(() ->
        {
            Map<String, ReportedPlayer> players = read(path, dateColumnName);
            players.put(normalized, new ReportedPlayer(normalized, display, dateReported));
            saveTo(path, players.values(), dateColumnName);
            replaceReportedPlayers(players);
            return null;
        });
    }

    synchronized void remove(String normalizedName) throws IOException
    {
        String normalized = DetectorService.normalizeName(normalizedName);
        withStoreLock(() ->
        {
            Map<String, ReportedPlayer> players = read(path, dateColumnName);
            players.remove(normalized);
            saveTo(path, players.values(), dateColumnName);
            replaceReportedPlayers(players);
            return null;
        });
    }

    synchronized int removeAll(Collection<String> normalizedNames) throws IOException
    {
        return withStoreLock(() ->
        {
            Map<String, ReportedPlayer> players = read(path, dateColumnName);
            int removed = 0;
            for (String normalizedName : normalizedNames)
            {
                String normalized = DetectorService.normalizeName(normalizedName);
                if (!normalized.isEmpty() && players.remove(normalized) != null)
                {
                    removed++;
                }
            }

            if (removed > 0)
            {
                saveTo(path, players.values(), dateColumnName);
            }
            replaceReportedPlayers(players);
            return removed;
        });
    }

    synchronized void clear() throws IOException
    {
        withStoreLock(() ->
        {
            Map<String, ReportedPlayer> players = new LinkedHashMap<>();
            saveTo(path, players.values(), dateColumnName);
            replaceReportedPlayers(players);
            return null;
        });
    }

    synchronized void exportTo(Path exportPath) throws IOException
    {
        saveTo(exportPath, reportedPlayers.values(), dateColumnName);
    }

    synchronized void importFrom(Path importPath, ImportMode mode) throws IOException
    {
        Map<String, ReportedPlayer> importedPlayers = read(importPath, dateColumnName);
        withStoreLock(() ->
        {
            Map<String, ReportedPlayer> players = mode == ImportMode.REPLACE
                ? new LinkedHashMap<>()
                : read(path, dateColumnName);
            players.putAll(importedPlayers);
            saveTo(path, players.values(), dateColumnName);
            replaceReportedPlayers(players);
            return null;
        });
    }

    synchronized boolean contains(String displayName)
    {
        return reportedPlayers.containsKey(DetectorService.normalizeName(displayName));
    }

    synchronized Set<String> getNormalizedNames()
    {
        return new LinkedHashSet<>(reportedPlayers.keySet());
    }

    synchronized Collection<ReportedPlayer> getReportedPlayers()
    {
        return Collections.unmodifiableCollection(new ArrayList<>(reportedPlayers.values()));
    }

    Path getPath()
    {
        return path;
    }

    private void replaceReportedPlayers(Map<String, ReportedPlayer> players)
    {
        reportedPlayers.clear();
        reportedPlayers.putAll(players);
    }

    private <T> T withStoreLock(LockedOperation<T> operation) throws IOException
    {
        Path lockPath = lockPath(path);
        Path lockParent = lockPath.getParent();
        if (lockParent != null)
        {
            Files.createDirectories(lockParent);
        }

        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockPath, ignored -> new ReentrantLock());
        jvmLock.lock();
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock())
        {
            return operation.run();
        }
        finally
        {
            jvmLock.unlock();
        }
    }

    private static Path lockPath(Path source)
    {
        Path absolute = source.toAbsolutePath().normalize();
        return absolute.resolveSibling(absolute.getFileName() + ".lock");
    }

    private static Map<String, ReportedPlayer> read(Path source, String dateColumnName) throws IOException
    {
        Map<String, ReportedPlayer> players = new LinkedHashMap<>();
        if (!Files.exists(source))
        {
            return players;
        }

        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i);
            if (line.trim().isEmpty())
            {
                continue;
            }

            List<String> values = parseCsvLine(line);
            if (values.size() != 3)
            {
                continue;
            }
            if (i == 0 && isHeader(values, dateColumnName))
            {
                continue;
            }

            String normalizedName = DetectorService.normalizeName(values.get(0));
            if (normalizedName.isEmpty())
            {
                continue;
            }

            String displayName = values.get(1).trim().isEmpty() ? normalizedName : values.get(1).trim();
            if (hasSpreadsheetFormulaPrefix(values.get(0))
                || hasSpreadsheetFormulaPrefix(normalizedName)
                || hasSpreadsheetFormulaPrefix(displayName))
            {
                continue;
            }

            Instant dateReported = parseInstant(values.get(2));
            players.put(normalizedName, new ReportedPlayer(normalizedName, displayName, dateReported));
        }
        return players;
    }

    private static boolean isHeader(List<String> values, String dateColumnName)
    {
        return "normalized_name".equals(values.get(0))
            && "display_name".equals(values.get(1))
            && dateColumnName.equals(values.get(2));
    }

    private static void saveTo(Path destination, Collection<ReportedPlayer> players, String dateColumnName)
        throws IOException
    {
        Path absoluteDestination = destination.toAbsolutePath();
        Path parent = absoluteDestination.getParent();
        Files.createDirectories(parent);

        List<String> lines = new ArrayList<>();
        lines.add("normalized_name,display_name," + dateColumnName);
        for (ReportedPlayer reportedPlayer : players)
        {
            lines.add(csv(reportedPlayer.getNormalizedName())
                + "," + csv(reportedPlayer.getDisplayName())
                + "," + csv(reportedPlayer.getDateReported().toString()));
        }

        Path temp = Files.createTempFile(parent, destination.getFileName().toString() + ".", ".tmp");
        try
        {
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, absoluteDestination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException ex)
        {
            Files.move(temp, absoluteDestination, StandardCopyOption.REPLACE_EXISTING);
        }
        finally
        {
            Files.deleteIfExists(temp);
        }
    }

    private static String csv(String value)
    {
        String safe = neutralizeSpreadsheetFormula(value == null ? "" : value);
        boolean quote = safe.indexOf(',') >= 0
            || safe.indexOf('"') >= 0
            || safe.indexOf('\n') >= 0
            || safe.indexOf('\r') >= 0;
        if (!quote)
        {
            return safe;
        }

        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String neutralizeSpreadsheetFormula(String value)
    {
        if (hasSpreadsheetFormulaPrefix(value))
        {
            return "'" + value;
        }
        return value;
    }

    private static boolean hasSpreadsheetFormulaPrefix(String value)
    {
        if (value == null)
        {
            return false;
        }

        char firstRaw = value.isEmpty() ? '\0' : value.charAt(0);
        if (firstRaw == '\t' || firstRaw == '\r' || firstRaw == '\n')
        {
            return true;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty())
        {
            return false;
        }

        char first = trimmed.charAt(0);
        return first == '='
            || first == '+'
            || first == '-'
            || first == '@';
    }

    private static List<String> parseCsvLine(String line)
    {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++)
        {
            char c = line.charAt(i);
            if (quoted)
            {
                if (c == '"')
                {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"')
                    {
                        current.append('"');
                        i++;
                    }
                    else
                    {
                        quoted = false;
                    }
                }
                else
                {
                    current.append(c);
                }
                continue;
            }

            if (c == '"')
            {
                quoted = true;
            }
            else if (c == ',')
            {
                values.add(current.toString());
                current.setLength(0);
            }
            else
            {
                current.append(c);
            }
        }

        values.add(current.toString());
        return values;
    }

    private static Instant parseInstant(String value)
    {
        try
        {
            return Instant.parse(value);
        }
        catch (DateTimeParseException ex)
        {
            return Instant.EPOCH;
        }
    }

    private interface LockedOperation<T>
    {
        T run() throws IOException;
    }
}
