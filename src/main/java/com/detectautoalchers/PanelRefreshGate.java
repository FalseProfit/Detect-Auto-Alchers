package com.detectautoalchers;

import java.util.List;

final class PanelRefreshGate
{
    private final long refreshIntervalMillis;
    private String lastSnapshot = "";
    private long lastRefreshMillis = Long.MIN_VALUE;

    PanelRefreshGate(long refreshIntervalMillis)
    {
        this.refreshIntervalMillis = refreshIntervalMillis;
    }

    synchronized boolean shouldRefresh(List<SuspicionResult> suspects, long nowMillis, boolean force)
    {
        return shouldRefresh(suspects, null, nowMillis, force);
    }

    synchronized boolean shouldRefresh(
        List<SuspicionResult> suspects,
        SuspicionResult examinedResult,
        long nowMillis,
        boolean force)
    {
        String snapshot = snapshot(suspects, examinedResult);
        boolean changed = !snapshot.equals(lastSnapshot);
        boolean intervalElapsed = lastRefreshMillis == Long.MIN_VALUE
            || nowMillis - lastRefreshMillis >= refreshIntervalMillis;
        if (!force && !changed && !intervalElapsed)
        {
            return false;
        }

        lastSnapshot = snapshot;
        lastRefreshMillis = nowMillis;
        return true;
    }

    synchronized void reset()
    {
        lastSnapshot = "";
        lastRefreshMillis = Long.MIN_VALUE;
    }

    private String snapshot(List<SuspicionResult> suspects, SuspicionResult examinedResult)
    {
        StringBuilder snapshot = new StringBuilder();
        for (SuspicionResult suspect : suspects)
        {
            if (snapshot.length() > 0)
            {
                snapshot.append('\n');
            }
            snapshot.append(suspect.stableEvidenceKey());
        }
        if (examinedResult != null)
        {
            if (snapshot.length() > 0)
            {
                snapshot.append('\n');
            }
            snapshot.append("examined|").append(examinedResult.stableEvidenceKey());
        }
        return snapshot.toString();
    }
}
