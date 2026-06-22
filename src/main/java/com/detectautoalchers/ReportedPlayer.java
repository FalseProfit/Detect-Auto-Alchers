package com.detectautoalchers;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class ReportedPlayer
{
    private final String normalizedName;
    private final String displayName;
    private final Instant dateReported;
    private final Set<String> accountUids;

    ReportedPlayer(String normalizedName, String displayName, Instant dateReported)
    {
        this(normalizedName, displayName, dateReported, Collections.emptySet());
    }

    ReportedPlayer(String normalizedName, String displayName, Instant dateReported, Set<String> accountUids)
    {
        this.normalizedName = normalizedName;
        this.displayName = displayName;
        this.dateReported = dateReported;
        this.accountUids = Collections.unmodifiableSet(new LinkedHashSet<>(accountUids));
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

    Set<String> getAccountUids()
    {
        return accountUids;
    }

    boolean containsAccountUid(String accountUid)
    {
        return accountUid != null && accountUids.contains(accountUid);
    }
}
