package com.detectautoalchers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DetectionPresetTest
{
    @Test
    public void conservativeHighConfidenceIsReachableByStandardStrongEvidence()
    {
        int standardStrongEvidenceScore = DetectorConfigSnapshot.STAFF_SCORE
            + DetectorConfigSnapshot.BEHAVIOR_SCORE
            + DetectorConfigSnapshot.CADENCE_SCORE
            + DetectorConfigSnapshot.HISCORE_SCORE;

        int conservativeHighThreshold = DetectionPreset.CONSERVATIVE.getSuspicionThreshold()
            + DetectionPreset.CONSERVATIVE.getHighConfidenceMargin();

        assertTrue(conservativeHighThreshold <= standardStrongEvidenceScore);
    }

    @Test
    public void presetConfidenceThresholdsMatchConfiguredStrictness()
    {
        assertEquals(90, DetectionPreset.CONSERVATIVE.getSuspicionThreshold());
        assertEquals(120, highConfidenceThreshold(DetectionPreset.CONSERVATIVE));
        assertEquals(60, DetectionPreset.BALANCED.getSuspicionThreshold());
        assertEquals(105, highConfidenceThreshold(DetectionPreset.BALANCED));
        assertEquals(45, DetectionPreset.AGGRESSIVE.getSuspicionThreshold());
        assertEquals(90, highConfidenceThreshold(DetectionPreset.AGGRESSIVE));
    }

    @Test
    public void presetReductionThresholdsMatchConfiguredValues()
    {
        assertEquals(1, DetectionPreset.CONSERVATIVE.getClueCollectionActivityThreshold());
        assertEquals(250, DetectionPreset.AGGRESSIVE.getNonMagicTotalLevelSuppressionThreshold());
    }

    private int highConfidenceThreshold(DetectionPreset preset)
    {
        return preset.getSuspicionThreshold() + preset.getHighConfidenceMargin();
    }
}
