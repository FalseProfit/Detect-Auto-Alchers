package com.detectautoalchers;

final class SuspicionResult
{
    private final String normalizedName;
    private final String displayName;
    private final int score;
    private final boolean suspicious;
    private final int castCount;
    private final boolean staffMatch;
    private final boolean behaviorMatch;
    private final boolean magicDominant;
    private final boolean consistentCadence;
    private final int magicLevel;
    private final int nonMagicSkillsAboveThreshold;
    private final String hiscoreStatus;
    private final int world;
    private final int distance;
    private final int weaponId;
    private final long lastSeenMillis;

    SuspicionResult(
        String normalizedName,
        String displayName,
        int score,
        boolean suspicious,
        int castCount,
        boolean staffMatch,
        boolean behaviorMatch,
        boolean magicDominant,
        boolean consistentCadence,
        int magicLevel,
        int nonMagicSkillsAboveThreshold,
        String hiscoreStatus,
        int world,
        int distance,
        int weaponId,
        long lastSeenMillis)
    {
        this.normalizedName = normalizedName;
        this.displayName = displayName;
        this.score = score;
        this.suspicious = suspicious;
        this.castCount = castCount;
        this.staffMatch = staffMatch;
        this.behaviorMatch = behaviorMatch;
        this.magicDominant = magicDominant;
        this.consistentCadence = consistentCadence;
        this.magicLevel = magicLevel;
        this.nonMagicSkillsAboveThreshold = nonMagicSkillsAboveThreshold;
        this.hiscoreStatus = hiscoreStatus;
        this.world = world;
        this.distance = distance;
        this.weaponId = weaponId;
        this.lastSeenMillis = lastSeenMillis;
    }

    String getNormalizedName()
    {
        return normalizedName;
    }

    String getDisplayName()
    {
        return displayName;
    }

    int getScore()
    {
        return score;
    }

    boolean isSuspicious()
    {
        return suspicious;
    }

    int getCastCount()
    {
        return castCount;
    }

    boolean isStaffMatch()
    {
        return staffMatch;
    }

    boolean isBehaviorMatch()
    {
        return behaviorMatch;
    }

    boolean isMagicDominant()
    {
        return magicDominant;
    }

    boolean isConsistentCadence()
    {
        return consistentCadence;
    }

    int getMagicLevel()
    {
        return magicLevel;
    }

    int getNonMagicSkillsAboveThreshold()
    {
        return nonMagicSkillsAboveThreshold;
    }

    String getHiscoreStatus()
    {
        return hiscoreStatus;
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

    long getLastSeenMillis()
    {
        return lastSeenMillis;
    }
}
