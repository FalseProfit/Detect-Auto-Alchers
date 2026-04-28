package com.detectautoalchers;

final class SuspicionResult
{
    private final String normalizedName;
    private final String displayName;
    private final int score;
    private final DetectionConfidence confidence;
    private final int castCount;
    private final boolean staffMatch;
    private final boolean behaviorMatch;
    private final boolean magicDominant;
    private final boolean highMagic;
    private final boolean consistentCadence;
    private final int magicLevel;
    private final int nonMagicSkillsAboveThreshold;
    private final int nonMagicTotalLevel;
    private final boolean matureAccountSuppressed;
    private final int clueScrollCompletions;
    private final int collectionLogItems;
    private final int clueAndCollectionLogTotal;
    private final boolean clueCollectionActivitySuppressed;
    private final String hiscoreStatus;
    private final int world;
    private final int distance;
    private final int weaponId;
    private final long lastSeenMillis;

    SuspicionResult(
        String normalizedName,
        String displayName,
        int score,
        DetectionConfidence confidence,
        int castCount,
        boolean staffMatch,
        boolean behaviorMatch,
        boolean magicDominant,
        boolean highMagic,
        boolean consistentCadence,
        int magicLevel,
        int nonMagicSkillsAboveThreshold,
        int nonMagicTotalLevel,
        boolean matureAccountSuppressed,
        int clueScrollCompletions,
        int collectionLogItems,
        int clueAndCollectionLogTotal,
        boolean clueCollectionActivitySuppressed,
        String hiscoreStatus,
        int world,
        int distance,
        int weaponId,
        long lastSeenMillis)
    {
        this.normalizedName = normalizedName;
        this.displayName = displayName;
        this.score = score;
        this.confidence = confidence;
        this.castCount = castCount;
        this.staffMatch = staffMatch;
        this.behaviorMatch = behaviorMatch;
        this.magicDominant = magicDominant;
        this.highMagic = highMagic;
        this.consistentCadence = consistentCadence;
        this.magicLevel = magicLevel;
        this.nonMagicSkillsAboveThreshold = nonMagicSkillsAboveThreshold;
        this.nonMagicTotalLevel = nonMagicTotalLevel;
        this.matureAccountSuppressed = matureAccountSuppressed;
        this.clueScrollCompletions = clueScrollCompletions;
        this.collectionLogItems = collectionLogItems;
        this.clueAndCollectionLogTotal = clueAndCollectionLogTotal;
        this.clueCollectionActivitySuppressed = clueCollectionActivitySuppressed;
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
        return confidence != DetectionConfidence.NONE;
    }

    DetectionConfidence getConfidence()
    {
        return confidence;
    }

    boolean isHighConfidence()
    {
        return confidence == DetectionConfidence.HIGH;
    }

    String getConfidenceLabel()
    {
        return confidence.getLabel();
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

    boolean isHighMagic()
    {
        return highMagic;
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

    int getNonMagicTotalLevel()
    {
        return nonMagicTotalLevel;
    }

    boolean isMatureAccountSuppressed()
    {
        return matureAccountSuppressed;
    }

    int getClueScrollCompletions()
    {
        return clueScrollCompletions;
    }

    int getCollectionLogItems()
    {
        return collectionLogItems;
    }

    int getClueAndCollectionLogTotal()
    {
        return clueAndCollectionLogTotal;
    }

    boolean isClueCollectionActivitySuppressed()
    {
        return clueCollectionActivitySuppressed;
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
