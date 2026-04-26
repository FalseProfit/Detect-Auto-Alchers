package com.detectautoalchers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import net.runelite.client.RuneLite;

final class ReportedPlayerStore
{
    private static final String HEADER = "normalized_name,display_name,date_reported";
    private static final String DIRECTORY = "detect-auto-alchers";
    private static final String FILE_NAME = "reported-players.csv";

    private final Path path;
    private final Map<String, ReportedPlayer> reportedPlayers = new LinkedHashMap<>();

    ReportedPlayerStore(Path path)
    {
        this.path = path;
    }

    static ReportedPlayerStore createDefault()
    {
        return new ReportedPlayerStore(RuneLite.RUNELITE_DIR.toPath().resolve(DIRECTORY).resolve(FILE_NAME));
    }

    synchronized void load() throws IOException
    {
        reportedPlayers.clear();
        if (!Files.exists(path))
        {
            return;
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i);
            if (i == 0 && HEADER.equals(line))
            {
                continue;
            }

            if (line.trim().isEmpty())
            {
                continue;
            }

            List<String> values = parseCsvLine(line);
            if (values.size() != 3)
            {
                continue;
            }

            String normalizedName = DetectorService.normalizeName(values.get(0));
            if (normalizedName.isEmpty())
            {
                continue;
            }

            String displayName = values.get(1).trim().isEmpty() ? normalizedName : values.get(1).trim();
            Instant dateReported = parseInstant(values.get(2));
            reportedPlayers.put(normalizedName, new ReportedPlayer(normalizedName, displayName, dateReported));
        }
    }

    synchronized void record(String normalizedName, String displayName, Instant dateReported) throws IOException
    {
        String normalized = DetectorService.normalizeName(normalizedName);
        if (normalized.isEmpty())
        {
            return;
        }

        String display = displayName == null || displayName.trim().isEmpty() ? normalized : displayName.trim();
        reportedPlayers.put(normalized, new ReportedPlayer(normalized, display, dateReported));
        save();
    }

    synchronized void clear() throws IOException
    {
        reportedPlayers.clear();
        save();
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

    private void save() throws IOException
    {
        Path parent = path.getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (ReportedPlayer reportedPlayer : reportedPlayers.values())
        {
            lines.add(csv(reportedPlayer.getNormalizedName())
                + "," + csv(reportedPlayer.getDisplayName())
                + "," + csv(reportedPlayer.getDateReported().toString()));
        }

        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String csv(String value)
    {
        String safe = value == null ? "" : value;
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
}
