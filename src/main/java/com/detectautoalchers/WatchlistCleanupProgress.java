package com.detectautoalchers;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class WatchlistCleanupProgress
{
    enum Status
    {
        PENDING,
        CHECKING,
        FOUND,
        NOT_FOUND,
        ERROR
    }

    private final Map<String, Status> statusesByName;
    private final Map<String, String> displayNamesByName;

    private WatchlistCleanupProgress(Map<String, Status> statusesByName, Map<String, String> displayNamesByName)
    {
        this.statusesByName = Collections.unmodifiableMap(new LinkedHashMap<>(statusesByName));
        this.displayNamesByName = Collections.unmodifiableMap(new LinkedHashMap<>(displayNamesByName));
    }

    static WatchlistCleanupProgress start(Collection<ReportedPlayer> watchedPlayers)
    {
        Map<String, Status> statuses = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        for (ReportedPlayer player : watchedPlayers)
        {
            statuses.put(player.getNormalizedName(), Status.PENDING);
            displayNames.put(player.getNormalizedName(), player.getDisplayName());
        }
        return new WatchlistCleanupProgress(statuses, displayNames);
    }

    WatchlistCleanupProgress withStatus(String normalizedName, Status status)
    {
        String normalized = DetectorService.normalizeName(normalizedName);
        if (!statusesByName.containsKey(normalized))
        {
            return this;
        }

        Map<String, Status> statuses = new LinkedHashMap<>(statusesByName);
        statuses.put(normalized, status);
        return new WatchlistCleanupProgress(statuses, displayNamesByName);
    }

    boolean isActive()
    {
        return !statusesByName.isEmpty();
    }

    int getTotal()
    {
        return statusesByName.size();
    }

    int getChecked()
    {
        int checked = 0;
        for (Status status : statusesByName.values())
        {
            if (status == Status.FOUND || status == Status.NOT_FOUND || status == Status.ERROR)
            {
                checked++;
            }
        }
        return checked;
    }

    int getRemaining()
    {
        return getTotal() - getChecked();
    }

    String getCurrentDisplayName()
    {
        for (Map.Entry<String, Status> entry : statusesByName.entrySet())
        {
            if (entry.getValue() == Status.CHECKING)
            {
                return displayNamesByName.getOrDefault(entry.getKey(), entry.getKey());
            }
        }
        return "";
    }

    Status getStatus(String normalizedName)
    {
        return statusesByName.get(DetectorService.normalizeName(normalizedName));
    }
}
