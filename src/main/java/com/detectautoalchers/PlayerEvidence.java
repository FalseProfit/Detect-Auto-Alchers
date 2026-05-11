package com.detectautoalchers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class PlayerEvidence
{
    private static final int DUPLICATE_OBSERVATION_TICK_GAP = 4;

    private final String normalizedName;
    private final Deque<AlchObservation> observations = new ArrayDeque<>();

    private String displayName;
    private int world;
    private int distance;
    private int weaponId = -1;
    private boolean basicFireStaff;
    private boolean fireRuneProvider;
    private long lastSeenMillis;
    private long lastHiscoreLookupMillis;
    private boolean hiscoreLookupInFlight;
    private HiscoreProfile hiscoreProfile = HiscoreProfile.unknown();
    private SuspicionResult lastResult;
    private int lastObservationTick = Integer.MIN_VALUE;
    private int lastObservationId = Integer.MIN_VALUE;
    private String lastObservationSource = "";

    PlayerEvidence(String normalizedName, String displayName)
    {
        this.normalizedName = normalizedName;
        this.displayName = displayName;
    }

    PlayerEvidence copy()
    {
        PlayerEvidence copy = new PlayerEvidence(normalizedName, displayName);
        copy.observations.addAll(observations);
        copy.world = world;
        copy.distance = distance;
        copy.weaponId = weaponId;
        copy.basicFireStaff = basicFireStaff;
        copy.fireRuneProvider = fireRuneProvider;
        copy.lastSeenMillis = lastSeenMillis;
        copy.lastHiscoreLookupMillis = lastHiscoreLookupMillis;
        copy.hiscoreLookupInFlight = hiscoreLookupInFlight;
        copy.hiscoreProfile = hiscoreProfile;
        copy.lastResult = lastResult;
        copy.lastObservationTick = lastObservationTick;
        copy.lastObservationId = lastObservationId;
        copy.lastObservationSource = lastObservationSource;
        return copy;
    }

    void updateSeen(String displayName, int world, int distance, int weaponId, long nowMillis)
    {
        this.displayName = displayName;
        this.world = world;
        this.distance = distance;
        this.weaponId = weaponId;
        this.basicFireStaff = StaffClassifier.isBasicFireStaff(weaponId);
        this.fireRuneProvider = StaffClassifier.isFireRuneProvider(weaponId);
        this.lastSeenMillis = nowMillis;
    }

    boolean recordObservation(String source, int id, int tick, long nowMillis, long windowMillis)
    {
        prune(nowMillis, windowMillis);
        if (tick == lastObservationTick)
        {
            return false;
        }

        boolean sameObservationStillActive = source.equals(lastObservationSource)
            && id == lastObservationId
            && tick - lastObservationTick <= DUPLICATE_OBSERVATION_TICK_GAP;
        if (sameObservationStillActive)
        {
            return false;
        }

        observations.addLast(new AlchObservation(nowMillis, tick, source, id));
        lastObservationTick = tick;
        lastObservationId = id;
        lastObservationSource = source;
        return true;
    }

    void prune(long nowMillis, long windowMillis)
    {
        while (!observations.isEmpty()
            && nowMillis - observations.peekFirst().getObservedAtMillis() > windowMillis)
        {
            observations.removeFirst();
        }
    }

    int getObservationCount(long nowMillis, long windowMillis)
    {
        prune(nowMillis, windowMillis);
        return observations.size();
    }

    boolean hasConsistentCadence(long nowMillis, long windowMillis, int requiredCount)
    {
        prune(nowMillis, windowMillis);
        if (observations.size() < requiredCount)
        {
            return false;
        }

        List<AlchObservation> snapshot = new ArrayList<>(observations);
        int consistentGaps = 0;
        for (int i = 1; i < snapshot.size(); i++)
        {
            int gap = snapshot.get(i).getTick() - snapshot.get(i - 1).getTick();
            if (gap >= 3 && gap <= 8)
            {
                consistentGaps++;
            }
        }

        return consistentGaps >= Math.max(1, requiredCount - 2);
    }

    String getNormalizedName()
    {
        return normalizedName;
    }

    String getDisplayName()
    {
        return displayName;
    }

    int getWorld()
    {
        return world;
    }

    int getDistance()
    {
        return distance;
    }

    int getWeaponId()
    {
        return weaponId;
    }

    boolean hasBasicFireStaff()
    {
        return basicFireStaff;
    }

    boolean hasFireRuneProvider()
    {
        return fireRuneProvider;
    }

    long getLastSeenMillis()
    {
        return lastSeenMillis;
    }

    long getLastHiscoreLookupMillis()
    {
        return lastHiscoreLookupMillis;
    }

    void setLastHiscoreLookupMillis(long lastHiscoreLookupMillis)
    {
        this.lastHiscoreLookupMillis = lastHiscoreLookupMillis;
    }

    boolean isHiscoreLookupInFlight()
    {
        return hiscoreLookupInFlight;
    }

    void setHiscoreLookupInFlight(boolean hiscoreLookupInFlight)
    {
        this.hiscoreLookupInFlight = hiscoreLookupInFlight;
    }

    HiscoreProfile getHiscoreProfile()
    {
        return hiscoreProfile;
    }

    void setHiscoreProfile(HiscoreProfile hiscoreProfile)
    {
        this.hiscoreProfile = hiscoreProfile;
    }

    SuspicionResult getLastResult()
    {
        return lastResult;
    }

    void setLastResult(SuspicionResult lastResult)
    {
        this.lastResult = lastResult;
    }
}
