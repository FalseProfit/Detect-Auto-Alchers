package com.detectautoalchers;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Singleton;

@Singleton
final class ReportedPlayerSession
{
    private static final String UNATTRIBUTED_SESSION = "<unattributed>";
    private final ConcurrentMap<String, Set<String>> reportedNamesByAccount = new ConcurrentHashMap<>();

    void record(String accountUid, String displayName)
    {
        String normalizedName = DetectorService.normalizeName(displayName);
        if (normalizedName.isEmpty())
        {
            return;
        }
        reportedNamesByAccount
            .computeIfAbsent(key(accountUid), ignored -> ConcurrentHashMap.newKeySet())
            .add(normalizedName);
    }

    void associateUnattributedReports(String accountUid)
    {
        if (accountUid == null)
        {
            return;
        }
        Set<String> unattributed = reportedNamesByAccount.remove(UNATTRIBUTED_SESSION);
        if (unattributed != null && !unattributed.isEmpty())
        {
            reportedNamesByAccount
                .computeIfAbsent(accountUid, ignored -> ConcurrentHashMap.newKeySet())
                .addAll(unattributed);
        }
    }

    boolean contains(String accountUid, String displayName)
    {
        Set<String> names = reportedNamesByAccount.get(key(accountUid));
        return names != null && names.contains(DetectorService.normalizeName(displayName));
    }

    Set<String> getNormalizedNames(String accountUid)
    {
        Set<String> names = reportedNamesByAccount.get(key(accountUid));
        return names == null
            ? Collections.emptySet()
            : new LinkedHashSet<>(names);
    }

    void clear()
    {
        reportedNamesByAccount.clear();
    }

    private static String key(String accountUid)
    {
        return accountUid == null ? UNATTRIBUTED_SESSION : accountUid;
    }
}
