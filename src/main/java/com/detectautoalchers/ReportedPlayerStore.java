package com.detectautoalchers;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import net.runelite.client.RuneLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ReportedPlayerStore
{
    private static final Logger log = LoggerFactory.getLogger(ReportedPlayerStore.class);
    private static final String DATE_REPORTED_COLUMN = "date_reported";
    private static final String DATE_WATCHED_COLUMN = "date_watched";
    private static final String DATE_ALLOWLISTED_COLUMN = "date_allowlisted";
    private static final String REPORTER_UIDS_COLUMN = "reporter_uids";
    private static final String MODIFIED_BY_UID_COLUMN = "modified_by_uid";
    private static final String DIRECTORY = "detect-auto-alchers";
    private static final String REPORTED_FILE_NAME = "reported-players.csv";
    private static final String WATCHLIST_FILE_NAME = "watchlist.csv";
    private static final String OVERRIDE_LIST_FILE_NAME = "override-list.csv";
    private static final int MAX_UIDS_PER_ENTRY = 128;
    private static final int MAX_UID_FIELD_LENGTH = 4_096;
    private static final String UNAVAILABLE_ACCOUNT_UID = Long.toUnsignedString(-1L);
    private static final ConcurrentMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private final Path path;
    private final String dateColumnName;
    private final String uidColumnName;
    private final boolean retainAllAccountUids;
    private final Map<String, ReportedPlayer> reportedPlayers = new LinkedHashMap<>();
    private FileFingerprint lastAttemptedFingerprint;

    ReportedPlayerStore(Path path)
    {
        this(path, DATE_REPORTED_COLUMN, REPORTER_UIDS_COLUMN, true);
    }

    ReportedPlayerStore(Path path, String dateColumnName)
    {
        this(path, dateColumnName, MODIFIED_BY_UID_COLUMN, false);
    }

    private ReportedPlayerStore(
        Path path,
        String dateColumnName,
        String uidColumnName,
        boolean retainAllAccountUids)
    {
        this.path = path;
        this.dateColumnName = dateColumnName;
        this.uidColumnName = uidColumnName;
        this.retainAllAccountUids = retainAllAccountUids;
    }

    static ReportedPlayerStore createDefault()
    {
        return create(REPORTED_FILE_NAME, DATE_REPORTED_COLUMN, REPORTER_UIDS_COLUMN, true);
    }

    static ReportedPlayerStore createWatchlist()
    {
        return create(WATCHLIST_FILE_NAME, DATE_WATCHED_COLUMN, MODIFIED_BY_UID_COLUMN, false);
    }

    static ReportedPlayerStore createOverrideList()
    {
        return create(OVERRIDE_LIST_FILE_NAME, DATE_ALLOWLISTED_COLUMN, MODIFIED_BY_UID_COLUMN, false);
    }

    private static ReportedPlayerStore create(
        String fileName,
        String dateColumnName,
        String uidColumnName,
        boolean retainAllAccountUids)
    {
        return new ReportedPlayerStore(
            RuneLite.RUNELITE_DIR.toPath().resolve(DIRECTORY).resolve(fileName),
            dateColumnName,
            uidColumnName,
            retainAllAccountUids
        );
    }

    synchronized void load() throws IOException
    {
        replaceReportedPlayers(withStoreLock(() -> read(path)));
        lastAttemptedFingerprint = fingerprint(path);
    }

    synchronized boolean reloadIfChanged() throws IOException
    {
        FileFingerprint observed = fingerprint(path);
        if (observed.equals(lastAttemptedFingerprint))
        {
            return false;
        }

        lastAttemptedFingerprint = observed;
        Map<String, ReportedPlayer> players = withStoreLock(() -> read(path));
        replaceReportedPlayers(players);
        lastAttemptedFingerprint = fingerprint(path);
        return true;
    }

    synchronized void record(String normalizedName, String displayName, Instant dateReported) throws IOException
    {
        record(normalizedName, displayName, dateReported, null);
    }

    synchronized void record(
        String normalizedName,
        String displayName,
        Instant dateReported,
        String accountUid) throws IOException
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

        String validAccountUid = normalizeAccountUid(accountUid);
        withStoreLock(() ->
        {
            Map<String, ReportedPlayer> players = read(path);
            ReportedPlayer existing = players.get(normalized);
            Set<String> accountUids = new LinkedHashSet<>();
            if (retainAllAccountUids && existing != null)
            {
                accountUids.addAll(existing.getAccountUids());
            }
            if (validAccountUid != null)
            {
                if (accountUids.size() >= MAX_UIDS_PER_ENTRY && !accountUids.contains(validAccountUid))
                {
                    throw new IOException("Reporter UID limit reached for " + normalized);
                }
                accountUids.add(validAccountUid);
            }

            players.put(normalized, new ReportedPlayer(normalized, display, dateReported, accountUids));
            saveTo(path, players.values());
            replaceReportedPlayers(players);
            lastAttemptedFingerprint = fingerprint(path);
            return null;
        });
    }

    synchronized void remove(String normalizedName) throws IOException
    {
        String normalized = DetectorService.normalizeName(normalizedName);
        withStoreLock(() ->
        {
            Map<String, ReportedPlayer> players = read(path);
            players.remove(normalized);
            saveTo(path, players.values());
            replaceReportedPlayers(players);
            lastAttemptedFingerprint = fingerprint(path);
            return null;
        });
    }

    synchronized int removeAll(Collection<String> normalizedNames) throws IOException
    {
        return withStoreLock(() ->
        {
            Map<String, ReportedPlayer> players = read(path);
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
                saveTo(path, players.values());
            }
            replaceReportedPlayers(players);
            lastAttemptedFingerprint = fingerprint(path);
            return removed;
        });
    }

    synchronized void clear() throws IOException
    {
        withStoreLock(() ->
        {
            Map<String, ReportedPlayer> players = new LinkedHashMap<>();
            saveTo(path, players.values());
            replaceReportedPlayers(players);
            lastAttemptedFingerprint = fingerprint(path);
            return null;
        });
    }

    synchronized void exportTo(Path exportPath) throws IOException
    {
        saveTo(exportPath, reportedPlayers.values());
    }

    synchronized void importFrom(Path importPath, ImportMode mode) throws IOException
    {
        Map<String, ReportedPlayer> importedPlayers = read(importPath);
        withStoreLock(() ->
        {
            Map<String, ReportedPlayer> players = mode == ImportMode.REPLACE
                ? new LinkedHashMap<>()
                : read(path);
            for (ReportedPlayer imported : importedPlayers.values())
            {
                if (mode == ImportMode.MERGE && retainAllAccountUids)
                {
                    ReportedPlayer existing = players.get(imported.getNormalizedName());
                    if (existing != null)
                    {
                        Set<String> mergedUids = new LinkedHashSet<>(existing.getAccountUids());
                        mergedUids.addAll(imported.getAccountUids());
                        imported = new ReportedPlayer(
                            imported.getNormalizedName(),
                            imported.getDisplayName(),
                            imported.getDateReported(),
                            mergedUids
                        );
                    }
                }
                players.put(imported.getNormalizedName(), imported);
            }
            saveTo(path, players.values());
            replaceReportedPlayers(players);
            lastAttemptedFingerprint = fingerprint(path);
            return null;
        });
    }

    synchronized boolean contains(String displayName)
    {
        return reportedPlayers.containsKey(DetectorService.normalizeName(displayName));
    }

    synchronized boolean containsForAccount(String displayName, String accountUid)
    {
        ReportedPlayer player = reportedPlayers.get(DetectorService.normalizeName(displayName));
        return player != null && player.containsAccountUid(accountUid);
    }

    synchronized Set<String> getNormalizedNames()
    {
        return new LinkedHashSet<>(reportedPlayers.keySet());
    }

    synchronized Set<String> getNormalizedNamesForAccount(String accountUid)
    {
        Set<String> names = new LinkedHashSet<>();
        if (accountUid == null)
        {
            return names;
        }
        for (ReportedPlayer player : reportedPlayers.values())
        {
            if (player.containsAccountUid(accountUid))
            {
                names.add(player.getNormalizedName());
            }
        }
        return names;
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

    private Map<String, ReportedPlayer> read(Path source) throws IOException
    {
        Map<String, ReportedPlayer> players = new LinkedHashMap<>();
        if (!Files.exists(source))
        {
            return players;
        }

        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        boolean warnedInvalidUid = false;
        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i);
            if (line.trim().isEmpty())
            {
                continue;
            }

            List<String> values = parseCsvLine(line);
            if (!values.isEmpty() && "normalized_name".equals(values.get(0)) && !isHeader(values))
            {
                throw new IOException("Unexpected player-list CSV header in " + source);
            }
            if (isHeader(values))
            {
                continue;
            }
            if (values.size() != 3 && values.size() != 4)
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

            Set<String> accountUids = Collections.emptySet();
            if (values.size() == 4)
            {
                accountUids = parseAccountUids(values.get(3));
                if (accountUids == null || (!retainAllAccountUids && accountUids.size() > 1))
                {
                    if (!warnedInvalidUid)
                    {
                        log.warn("Ignoring player-list rows with invalid account UID metadata in {}", source);
                        warnedInvalidUid = true;
                    }
                    continue;
                }
            }

            Instant dateReported = parseInstant(values.get(2));
            players.put(
                normalizedName,
                new ReportedPlayer(normalizedName, displayName, dateReported, accountUids)
            );
        }
        return players;
    }

    private boolean isHeader(List<String> values)
    {
        if (values.size() != 3 && values.size() != 4)
        {
            return false;
        }
        return "normalized_name".equals(values.get(0))
            && "display_name".equals(values.get(1))
            && dateColumnName.equals(values.get(2))
            && (values.size() == 3 || uidColumnName.equals(values.get(3)));
    }

    private void saveTo(Path destination, Collection<ReportedPlayer> players) throws IOException
    {
        Path absoluteDestination = destination.toAbsolutePath();
        Path parent = absoluteDestination.getParent();
        Files.createDirectories(parent);

        List<String> lines = new ArrayList<>();
        lines.add("normalized_name,display_name," + dateColumnName + "," + uidColumnName);
        for (ReportedPlayer reportedPlayer : players)
        {
            lines.add(csv(reportedPlayer.getNormalizedName())
                + "," + csv(reportedPlayer.getDisplayName())
                + "," + csv(reportedPlayer.getDateReported().toString())
                + "," + csv(String.join(";", reportedPlayer.getAccountUids())));
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

    private static Set<String> parseAccountUids(String value)
    {
        if (value == null || value.isEmpty())
        {
            return Collections.emptySet();
        }
        if (value.length() > MAX_UID_FIELD_LENGTH)
        {
            return null;
        }

        Set<String> accountUids = new LinkedHashSet<>();
        for (String token : value.split(";", -1))
        {
            String accountUid = normalizeAccountUid(token);
            if (accountUid == null)
            {
                return null;
            }
            if (!accountUids.add(accountUid))
            {
                continue;
            }
            if (accountUids.size() > MAX_UIDS_PER_ENTRY)
            {
                return null;
            }
        }
        return accountUids;
    }

    private static String normalizeAccountUid(String value)
    {
        if (value == null || value.isEmpty() || value.length() > 20)
        {
            return null;
        }
        for (int i = 0; i < value.length(); i++)
        {
            if (!Character.isDigit(value.charAt(i)))
            {
                return null;
            }
        }
        try
        {
            String normalized = Long.toUnsignedString(Long.parseUnsignedLong(value));
            return UNAVAILABLE_ACCOUNT_UID.equals(normalized) ? null : normalized;
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private static FileFingerprint fingerprint(Path source) throws IOException
    {
        if (!Files.exists(source))
        {
            return FileFingerprint.MISSING;
        }
        BasicFileAttributes attributes;
        try
        {
            attributes = Files.readAttributes(source, BasicFileAttributes.class);
        }
        catch (NoSuchFileException ex)
        {
            return FileFingerprint.MISSING;
        }
        Object fileKey = attributes.fileKey();
        return new FileFingerprint(
            true,
            attributes.lastModifiedTime(),
            attributes.size(),
            fileKey == null ? "" : fileKey.toString()
        );
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
        return hasSpreadsheetFormulaPrefix(value) ? "'" + value : value;
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
        return first == '=' || first == '+' || first == '-' || first == '@';
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

    private static final class FileFingerprint
    {
        private static final FileFingerprint MISSING = new FileFingerprint(false, FileTime.fromMillis(0L), 0L, "");
        private final boolean exists;
        private final FileTime modifiedTime;
        private final long size;
        private final String fileKey;

        private FileFingerprint(boolean exists, FileTime modifiedTime, long size, String fileKey)
        {
            this.exists = exists;
            this.modifiedTime = modifiedTime;
            this.size = size;
            this.fileKey = fileKey;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof FileFingerprint))
            {
                return false;
            }
            FileFingerprint that = (FileFingerprint) other;
            return exists == that.exists
                && size == that.size
                && modifiedTime.equals(that.modifiedTime)
                && fileKey.equals(that.fileKey);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(exists, modifiedTime, size, fileKey);
        }
    }
}
