package com.detectautoalchers;

import java.time.Instant;

final class ReportedPlayer
{
    private final String normalizedName;
    private final String displayName;
    private final Instant dateReported;

    ReportedPlayer(String normalizedName, String displayName, Instant dateReported)
    {
        this.normalizedName = normalizedName;
        this.displayName = displayName;
        this.dateReported = dateReported;
    }

    String getNormalizedName()
    {
        return normalizedName;
    }

    String getDisplayName()
    {
        return displayName;
    }

    Instant getDateReported()
    {
        return dateReported;
    }
}
