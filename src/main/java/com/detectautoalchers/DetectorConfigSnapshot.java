package com.detectautoalchers;

import java.util.Collections;
import java.util.Set;

final class DetectorConfigSnapshot
{
    static final int STAFF_SCORE = 30;
    static final int BEHAVIOR_SCORE = 50;
    static final int HISCORE_SCORE = 30;
    static final int CADENCE_SCORE = 10;

    private final int radius;
    private final long observationWindowMillis;
    private final int castThreshold;
    private final int suspicionThreshold;
    private final boolean requireFireStaff;
    private final boolean includeFireRuneStaves;
    private final boolean enableHiscoreScoring;
    private final int magicLevelThreshold;
    private final int nonMagicSkillThreshold;
    private final int allowedNonMagicSkillsAboveThreshold;
    private final long hiscoreCooldownMillis;
    private final Set<Integer> alchemyAnimationIds;
    private final Set<Integer> alchemySpotAnimationIds;
    private final boolean showOverlay;
    private final boolean colorMenuEntries;

    DetectorConfigSnapshot(
        int radius,
        long observationWindowMillis,
        int castThreshold,
        int suspicionThreshold,
        boolean requireFireStaff,
        boolean includeFireRuneStaves,
        boolean enableHiscoreScoring,
        int magicLevelThreshold,
        int nonMagicSkillThreshold,
        int allowedNonMagicSkillsAboveThreshold,
        long hiscoreCooldownMillis,
        Set<Integer> alchemyAnimationIds,
        Set<Integer> alchemySpotAnimationIds,
        boolean showOverlay,
        boolean colorMenuEntries)
    {
        this.radius = radius;
        this.observationWindowMillis = observationWindowMillis;
        this.castThreshold = castThreshold;
        this.suspicionThreshold = suspicionThreshold;
        this.requireFireStaff = requireFireStaff;
        this.includeFireRuneStaves = includeFireRuneStaves;
        this.enableHiscoreScoring = enableHiscoreScoring;
        this.magicLevelThreshold = magicLevelThreshold;
        this.nonMagicSkillThreshold = nonMagicSkillThreshold;
        this.allowedNonMagicSkillsAboveThreshold = allowedNonMagicSkillsAboveThreshold;
        this.hiscoreCooldownMillis = hiscoreCooldownMillis;
        this.alchemyAnimationIds = Collections.unmodifiableSet(alchemyAnimationIds);
        this.alchemySpotAnimationIds = Collections.unmodifiableSet(alchemySpotAnimationIds);
        this.showOverlay = showOverlay;
        this.colorMenuEntries = colorMenuEntries;
    }

    static DetectorConfigSnapshot from(DetectAutoAlchersConfig config)
    {
        return new DetectorConfigSnapshot(
            config.radius(),
            config.observationWindowSeconds() * 1000L,
            config.castThreshold(),
            config.suspicionThreshold(),
            config.requireFireStaff(),
            config.includeFireRuneStaves(),
            config.enableHiscoreScoring(),
            config.magicLevelThreshold(),
            config.nonMagicSkillThreshold(),
            config.allowedNonMagicSkillsAboveThreshold(),
            config.hiscoreCooldownMinutes() * 60_000L,
            IdListParser.parse(config.alchemyAnimationIds()),
            IdListParser.parse(config.alchemySpotAnimationIds()),
            config.showOverlay(),
            config.colorMenuEntries()
        );
    }

    static DetectorConfigSnapshot defaultsForTesting()
    {
        return new DetectorConfigSnapshot(
            15,
            60_000L,
            5,
            80,
            true,
            false,
            true,
            21,
            10,
            2,
            15 * 60_000L,
            IdListParser.parse("713"),
            IdListParser.parse("112,113"),
            true,
            true
        );
    }

    int getRadius()
    {
        return radius;
    }

    long getObservationWindowMillis()
    {
        return observationWindowMillis;
    }

    int getCastThreshold()
    {
        return castThreshold;
    }

    int getSuspicionThreshold()
    {
        return suspicionThreshold;
    }

    boolean isRequireFireStaff()
    {
        return requireFireStaff;
    }

    boolean isIncludeFireRuneStaves()
    {
        return includeFireRuneStaves;
    }

    boolean isEnableHiscoreScoring()
    {
        return enableHiscoreScoring;
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

    long getHiscoreCooldownMillis()
    {
        return hiscoreCooldownMillis;
    }

    Set<Integer> getAlchemySpotAnimationIds()
    {
        return alchemySpotAnimationIds;
    }

    boolean isAlchemyAnimation(int animationId)
    {
        return alchemyAnimationIds.contains(animationId);
    }

    boolean isShowOverlay()
    {
        return showOverlay;
    }

    boolean isColorMenuEntries()
    {
        return colorMenuEntries;
    }
}
