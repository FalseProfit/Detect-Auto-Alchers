package com.detectautoalchers;

enum DetectionPreset
{
    CONSERVATIVE(10, 90, 30, true, false, 53, 50, 1, 150, 150, 1, 100),
    BALANCED(5, 60, 45, true, false, 53, 50, 2, 150, 100, 4, 100),
    AGGRESSIVE(3, 45, 45, true, true, 21, 50, 3, 250, 75, 8, 75);

    private final int castThreshold;
    private final int suspicionThreshold;
    private final int highConfidenceMargin;
    private final boolean requireFireStaff;
    private final boolean includeFireRuneStaves;
    private final int magicLevelThreshold;
    private final int nonMagicSkillThreshold;
    private final int allowedNonMagicSkillsAboveThreshold;
    private final int nonMagicTotalLevelSuppressionThreshold;
    private final int matureAccountScorePenalty;
    private final int clueCollectionActivityThreshold;
    private final int clueCollectionActivityScorePenalty;

    DetectionPreset(
        int castThreshold,
        int suspicionThreshold,
        int highConfidenceMargin,
        boolean requireFireStaff,
        boolean includeFireRuneStaves,
        int magicLevelThreshold,
        int nonMagicSkillThreshold,
        int allowedNonMagicSkillsAboveThreshold,
        int nonMagicTotalLevelSuppressionThreshold,
        int matureAccountScorePenalty,
        int clueCollectionActivityThreshold,
        int clueCollectionActivityScorePenalty)
    {
        this.castThreshold = castThreshold;
        this.suspicionThreshold = suspicionThreshold;
        this.highConfidenceMargin = highConfidenceMargin;
        this.requireFireStaff = requireFireStaff;
        this.includeFireRuneStaves = includeFireRuneStaves;
        this.magicLevelThreshold = magicLevelThreshold;
        this.nonMagicSkillThreshold = nonMagicSkillThreshold;
        this.allowedNonMagicSkillsAboveThreshold = allowedNonMagicSkillsAboveThreshold;
        this.nonMagicTotalLevelSuppressionThreshold = nonMagicTotalLevelSuppressionThreshold;
        this.matureAccountScorePenalty = matureAccountScorePenalty;
        this.clueCollectionActivityThreshold = clueCollectionActivityThreshold;
        this.clueCollectionActivityScorePenalty = clueCollectionActivityScorePenalty;
    }

    int getCastThreshold()
    {
        return castThreshold;
    }

    int getSuspicionThreshold()
    {
        return suspicionThreshold;
    }

    int getHighConfidenceMargin()
    {
        return highConfidenceMargin;
    }

    boolean isRequireFireStaff()
    {
        return requireFireStaff;
    }

    boolean isIncludeFireRuneStaves()
    {
        return includeFireRuneStaves;
    }

    int getMagicLevelThreshold()
    {
        return magicLevelThreshold;
    }

    int getNonMagicSkillThreshold()
    {
        return nonMagicSkillThreshold;
    }

    int getAllowedNonMagicSkillsAboveThreshold()
    {
        return allowedNonMagicSkillsAboveThreshold;
    }

    int getNonMagicTotalLevelSuppressionThreshold()
    {
        return nonMagicTotalLevelSuppressionThreshold;
    }

    int getMatureAccountScorePenalty()
    {
        return matureAccountScorePenalty;
    }

    int getClueCollectionActivityThreshold()
    {
        return clueCollectionActivityThreshold;
    }

    int getClueCollectionActivityScorePenalty()
    {
        return clueCollectionActivityScorePenalty;
    }
}
