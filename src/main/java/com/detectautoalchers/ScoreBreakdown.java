package com.detectautoalchers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class ScoreBreakdown
{
    private static final ScoreBreakdown EMPTY = new ScoreBreakdown(
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        false,
        false,
        false
    );

    private final int staffScore;
    private final int behaviorScore;
    private final int hiscoreScore;
    private final int highMagicScore;
    private final int cadenceScore;
    private final int matureAccountPenalty;
    private final int clueCollectionPenalty;
    private final boolean castGatePassed;
    private final boolean staffGatePassed;
    private final boolean detectionGatePassed;

    ScoreBreakdown(
        int staffScore,
        int behaviorScore,
        int hiscoreScore,
        int highMagicScore,
        int cadenceScore,
        int matureAccountPenalty,
        int clueCollectionPenalty,
        boolean castGatePassed,
        boolean staffGatePassed,
        boolean detectionGatePassed)
    {
        this.staffScore = staffScore;
        this.behaviorScore = behaviorScore;
        this.hiscoreScore = hiscoreScore;
        this.highMagicScore = highMagicScore;
        this.cadenceScore = cadenceScore;
        this.matureAccountPenalty = matureAccountPenalty;
        this.clueCollectionPenalty = clueCollectionPenalty;
        this.castGatePassed = castGatePassed;
        this.staffGatePassed = staffGatePassed;
        this.detectionGatePassed = detectionGatePassed;
    }

    static ScoreBreakdown empty()
    {
        return EMPTY;
    }

    int getPositiveTotal()
    {
        return staffScore + behaviorScore + hiscoreScore + highMagicScore + cadenceScore;
    }

    int getPenaltyTotal()
    {
        return matureAccountPenalty + clueCollectionPenalty;
    }

    int getFinalTotal()
    {
        return Math.max(0, getPositiveTotal() - getPenaltyTotal());
    }

    boolean isCastGatePassed()
    {
        return castGatePassed;
    }

    boolean isStaffGatePassed()
    {
        return staffGatePassed;
    }

    boolean isDetectionGatePassed()
    {
        return detectionGatePassed;
    }

    List<String> getScoreLabels()
    {
        List<String> labels = new ArrayList<>();
        addPositive(labels, "staff", staffScore);
        addPositive(labels, "casts", behaviorScore);
        addPositive(labels, "cadence", cadenceScore);
        addPositive(labels, "magic profile", hiscoreScore);
        addPositive(labels, "99 magic", highMagicScore);
        addPenalty(labels, "non-magic total", matureAccountPenalty);
        addPenalty(labels, "clues/log", clueCollectionPenalty);
        return Collections.unmodifiableList(labels);
    }

    String stableKey()
    {
        return staffScore
            + ":" + behaviorScore
            + ":" + hiscoreScore
            + ":" + highMagicScore
            + ":" + cadenceScore
            + ":" + matureAccountPenalty
            + ":" + clueCollectionPenalty
            + ":" + castGatePassed
            + ":" + staffGatePassed
            + ":" + detectionGatePassed;
    }

    private static void addPositive(List<String> labels, String name, int score)
    {
        if (score > 0)
        {
            labels.add(name + " +" + score);
        }
    }

    private static void addPenalty(List<String> labels, String name, int penalty)
    {
        if (penalty > 0)
        {
            labels.add(name + " -" + penalty);
        }
    }

    @Override
    public boolean equals(Object object)
    {
        if (this == object)
        {
            return true;
        }
        if (!(object instanceof ScoreBreakdown))
        {
            return false;
        }
        ScoreBreakdown that = (ScoreBreakdown) object;
        return staffScore == that.staffScore
            && behaviorScore == that.behaviorScore
            && hiscoreScore == that.hiscoreScore
            && highMagicScore == that.highMagicScore
            && cadenceScore == that.cadenceScore
            && matureAccountPenalty == that.matureAccountPenalty
            && clueCollectionPenalty == that.clueCollectionPenalty
            && castGatePassed == that.castGatePassed
            && staffGatePassed == that.staffGatePassed
            && detectionGatePassed == that.detectionGatePassed;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(
            staffScore,
            behaviorScore,
            hiscoreScore,
            highMagicScore,
            cadenceScore,
            matureAccountPenalty,
            clueCollectionPenalty,
            castGatePassed,
            staffGatePassed,
            detectionGatePassed
        );
    }
}
