package com.detectautoalchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DetectorServiceTest
{
    @Test
    public void flagsPlayerWithStaffBehaviorAndMagicDominantHiscore()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Alch Bot", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        for (int i = 0; i < 5; i++)
        {
            service.recordAlchObservation("Alch Bot", "animation", 713, 100 + (i * 5), now + (i * 600L), config);
        }
        service.applyHiscore(name, HiscoreProfile.found(55, 1, true));

        service.recompute(config, now + 3_000L);
        List<SuspicionResult> suspects = service.getSuspiciousResults();

        assertEquals(1, suspects.size());
        assertEquals("Alch Bot", suspects.get(0).getDisplayName());
        assertTrue(suspects.get(0).isSuspicious());
        assertTrue(suspects.get(0).isHighConfidence());
        assertEquals(DetectionConfidence.HIGH, service.getConfidence("Alch Bot"));
        assertEquals(120, suspects.get(0).getScore());
    }

    @Test
    public void staffAndBehaviorFlagsModerateConfidence()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        service.updatePlayer("Moderate Alcher", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Moderate Alcher", now, config);

        service.recompute(config, now + 3_000L);
        List<SuspicionResult> suspects = service.getSuspiciousResults();

        assertEquals(1, suspects.size());
        assertEquals(DetectionConfidence.MODERATE, suspects.get(0).getConfidence());
        assertEquals(DetectionConfidence.MODERATE, service.getConfidence("Moderate Alcher"));
        assertEquals(90, suspects.get(0).getScore());
    }

    @Test
    public void returnsLatestScoresForAllCurrentEvidence()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String suspiciousName = service.updatePlayer("Scored Alcher", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Scored Alcher", now, config);
        service.applyHiscore(suspiciousName, HiscoreProfile.found(55, 1, 40, true));
        service.updatePlayer("Idle Staff", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);

        service.recompute(config, now + 3_000L);
        Map<String, Integer> scoresByName = service.getScoresByName();

        assertEquals(Integer.valueOf(120), scoresByName.get("scored alcher"));
        assertEquals(Integer.valueOf(30), scoresByName.get("idle staff"));
        assertFalse(service.isSuspicious("Idle Staff"));
        assertFalse(scoresByName.containsKey("unknown player"));
    }

    @Test
    public void suppressedNamesAreExcludedFromScoreMap()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        service.updatePlayer("Suppressed Alcher", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Suppressed Alcher", now, config);
        service.recompute(config, now + 3_000L);

        service.suppressName("Suppressed Alcher");

        assertFalse(service.getScoresByName().containsKey("suppressed alcher"));
        assertFalse(service.getScoresByName().containsKey("unknown player"));
    }

    @Test
    public void magicThresholdSuppressesAllScoreForKnownLowMagicProfile()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot initialConfig = DetectorConfigSnapshot.defaultsForTesting();
        DetectorConfigSnapshot raisedMagicThresholdConfig = configWithMagicThreshold(70);
        long now = 10_000L;

        String name = service.updatePlayer("Low Magic Alcher", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Low Magic Alcher", now, initialConfig);
        service.applyHiscore(name, HiscoreProfile.found(55, 1, 40, true));

        service.recompute(initialConfig, now + 3_000L);
        assertEquals(1, service.getSuspiciousResults().size());

        service.recompute(raisedMagicThresholdConfig, now + 3_000L);

        assertTrue(service.getSuspiciousResults().isEmpty());
        assertFalse(service.isSuspicious("Low Magic Alcher"));
        assertEquals(DetectionConfidence.NONE, service.getConfidence("Low Magic Alcher"));
    }

    @Test
    public void highConfidenceThresholdCannotFallBelowModerateThreshold()
    {
        DetectorConfigSnapshot config = configWithConfidenceThresholds(100, 80);

        assertEquals(100, config.getSuspicionThreshold());
        assertEquals(101, config.getHighConfidenceThreshold());
    }

    @Test
    public void configSnapshotUsesHighConfidenceMargin()
    {
        DetectorConfigSnapshot config = DetectorConfigSnapshot.from(new DetectAutoAlchersConfig()
        {
            @Override
            public int suspicionThreshold()
            {
                return 90;
            }

            @Override
            public int highConfidenceMargin()
            {
                return 25;
            }
        });

        assertEquals(90, config.getSuspicionThreshold());
        assertEquals(115, config.getHighConfidenceThreshold());
    }

    @Test
    public void matureAccountPenaltyDropsScoreBelowThreshold()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Played Alcher", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        for (int i = 0; i < 5; i++)
        {
            service.recordAlchObservation("Played Alcher", "animation", 713, 100 + (i * 5), now + (i * 600L), config);
        }
        service.applyHiscore(name, HiscoreProfile.found(89, 2, 125, true));

        service.recompute(config, now + 3_000L);

        assertTrue(service.getSuspiciousResults().isEmpty());
    }

    @Test
    public void matureAccountBelowThresholdStillFlags()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Almost Mature", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        for (int i = 0; i < 5; i++)
        {
            service.recordAlchObservation("Almost Mature", "animation", 713, 100 + (i * 5), now + (i * 600L), config);
        }
        service.applyHiscore(name, HiscoreProfile.found(89, 2, 124, true));

        service.recompute(config, now + 3_000L);

        assertEquals(1, service.getSuspiciousResults().size());
    }

    @Test
    public void clueCollectionPenaltyDropsScoreBelowThreshold()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Clue Player", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Clue Player", now, config);
        service.applyHiscore(name, HiscoreProfile.found(89, 2, 40, 6, 0, true));

        service.recompute(config, now + 3_000L);

        assertTrue(service.getSuspiciousResults().isEmpty());
        assertFalse(service.isSuspicious("Clue Player"));
    }

    @Test
    public void clueCollectionPenaltyStacksWithNonMagicTotalPenalty()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Played Max Mage", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Played Max Mage", now, config);
        service.applyHiscore(name, HiscoreProfile.found(99, 2, 125, 6, 0, true));

        service.recompute(config, now + 3_000L);

        assertTrue(service.getSuspiciousResults().isEmpty());
        assertFalse(service.isSuspicious("Played Max Mage"));
    }

    @Test
    public void clueCollectionAtThresholdDoesNotPenalize()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Five Clues", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Five Clues", now, config);
        service.applyHiscore(name, HiscoreProfile.found(89, 2, 40, 3, 2, true));

        service.recompute(config, now + 3_000L);
        List<SuspicionResult> suspects = service.getSuspiciousResults();

        assertEquals(1, suspects.size());
        assertFalse(suspects.get(0).isClueCollectionActivitySuppressed());
        assertEquals(120, suspects.get(0).getScore());
    }

    @Test
    public void configuredClueCollectionThresholdAndPenaltyAffectScore()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = configWithClueCollectionReduction(10, 40);
        long now = 10_000L;

        String name = service.updatePlayer("Custom Clues", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Custom Clues", now, config);
        service.applyHiscore(name, HiscoreProfile.found(89, 2, 40, 8, 3, true));

        service.recompute(config, now + 3_000L);
        List<SuspicionResult> suspects = service.getSuspiciousResults();

        assertEquals(1, suspects.size());
        assertTrue(suspects.get(0).isClueCollectionActivitySuppressed());
        assertEquals(11, suspects.get(0).getClueAndCollectionLogTotal());
        assertEquals(80, suspects.get(0).getScore());
    }

    @Test
    public void highMagicAddsScoreAndCanCounterMaturePenalty()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Maxed Magic", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        for (int i = 0; i < 5; i++)
        {
            service.recordAlchObservation("Maxed Magic", "animation", 713, 100 + (i * 5), now + (i * 600L), config);
        }
        service.applyHiscore(name, HiscoreProfile.found(99, 2, 125, true));

        service.recompute(config, now + 3_000L);
        List<SuspicionResult> suspects = service.getSuspiciousResults();

        assertEquals(1, suspects.size());
        assertTrue(suspects.get(0).isHighMagic());
        assertTrue(suspects.get(0).isMatureAccountSuppressed());
        assertEquals(120, suspects.get(0).getScore());
    }

    @Test
    public void magicBelow99DoesNotReceiveHighMagicScore()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Almost Maxed Magic", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Almost Maxed Magic", now, config);
        service.applyHiscore(name, HiscoreProfile.found(98, 2, 40, true));

        service.recompute(config, now + 3_000L);
        List<SuspicionResult> suspects = service.getSuspiciousResults();

        assertEquals(1, suspects.size());
        assertFalse(suspects.get(0).isHighMagic());
        assertEquals(120, suspects.get(0).getScore());
    }

    @Test
    public void doesNotFlagWithoutAlchemyBehaviorEvenWhenScoreIsHigh()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Idle Staff", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        service.applyHiscore(name, HiscoreProfile.found(99, 1, true));

        service.recompute(config, now + 3_000L);

        assertTrue(service.getSuspiciousResults().isEmpty());
        assertFalse(service.isSuspicious("Idle Staff"));
        assertEquals(DetectionConfidence.NONE, service.getConfidence("Idle Staff"));
    }

    @Test
    public void zeroCastThresholdFlagsIdleStaffWhenScoreMeetsThreshold()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = configWithCastAndModerateThreshold(0, 60);
        long now = 10_000L;

        String name = service.updatePlayer("Idle Score Bot", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        service.applyHiscore(name, HiscoreProfile.found(55, 1, true));

        service.recompute(config, now + 3_000L);
        List<SuspicionResult> suspects = service.getSuspiciousResults();

        assertEquals(1, suspects.size());
        assertEquals("Idle Score Bot", suspects.get(0).getDisplayName());
        assertEquals(DetectionConfidence.MODERATE, suspects.get(0).getConfidence());
        assertEquals(60, suspects.get(0).getScore());
        assertEquals(0, suspects.get(0).getCastCount());
        assertFalse(suspects.get(0).isBehaviorMatch());
    }

    @Test
    public void positiveCastThresholdStillRequiresAlchemyBehavior()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = configWithCastAndModerateThreshold(5, 60);
        long now = 10_000L;

        String name = service.updatePlayer("Idle Score Bot", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        service.applyHiscore(name, HiscoreProfile.found(55, 1, true));

        service.recompute(config, now + 3_000L);

        assertTrue(service.getSuspiciousResults().isEmpty());
        assertFalse(service.isSuspicious("Idle Score Bot"));
        assertEquals(DetectionConfidence.NONE, service.getConfidence("Idle Score Bot"));
        assertEquals(Integer.valueOf(60), service.getScoresByName().get("idle score bot"));
    }

    @Test
    public void zeroCastThresholdDoesNotGrantBehaviorScore()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = configWithCastAndModerateThreshold(0, 60);
        long now = 10_000L;

        service.updatePlayer("Staff Only", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);

        service.recompute(config, now + 3_000L);

        assertTrue(service.getSuspiciousResults().isEmpty());
        assertEquals(Integer.valueOf(30), service.getScoresByName().get("staff only"));
        assertFalse(service.isSuspicious("Staff Only"));
    }

    @Test
    public void suppressedNamesAreExcludedFromResults()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Reported Alcher", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        for (int i = 0; i < 5; i++)
        {
            service.recordAlchObservation("Reported Alcher", "animation", 713, 100 + (i * 5), now + (i * 600L), config);
        }
        service.applyHiscore(name, HiscoreProfile.found(55, 1, 40, true));
        service.recompute(config, now + 3_000L);
        assertEquals(1, service.getSuspiciousResults().size());

        service.suppressName("Reported Alcher");

        assertTrue(service.getSuspiciousResults().isEmpty());
        assertFalse(service.isSuspicious("Reported Alcher"));
        assertFalse(service.recordAlchObservation("Reported Alcher", "animation", 713, 200, now + 4_000L, config));
    }

    @Test
    public void findsSuspiciousNameFromMenuTarget()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Menu Alcher", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        for (int i = 0; i < 5; i++)
        {
            service.recordAlchObservation("Menu Alcher", "animation", 713, 100 + (i * 5), now + (i * 600L), config);
        }
        service.applyHiscore(name, HiscoreProfile.found(55, 1, 40, true));
        service.recompute(config, now + 3_000L);

        assertEquals("menu alcher", service.findSuspiciousNameFromTarget("<col=ffffff>Menu Alcher<col=ffff00> (level-3)"));
    }

    @Test
    public void doesNotFlagWithoutRequiredStaff()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        String name = service.updatePlayer("Mage Legit", 301, 4, -1, now);
        for (int i = 0; i < 5; i++)
        {
            service.recordAlchObservation("Mage Legit", "animation", 713, 100 + (i * 5), now + (i * 600L), config);
        }
        service.applyHiscore(name, HiscoreProfile.found(55, 1, true));

        service.recompute(config, now + 3_000L);

        assertTrue(service.getSuspiciousResults().isEmpty());
    }

    @Test
    public void prunesOldObservations()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;

        service.updatePlayer("Old Alcher", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        for (int i = 0; i < 5; i++)
        {
            service.recordAlchObservation("Old Alcher", "animation", 713, 100 + (i * 5), now + (i * 600L), config);
        }

        service.recompute(config, now + config.getObservationWindowMillis() + 10_000L);

        assertTrue(service.getSuspiciousResults().isEmpty());
    }

    @Test
    public void normalizesNamesForCacheAndLookup()
    {
        assertEquals("auto bot", DetectorService.normalizeName("<img=1>Auto\u00A0\u00A0Bot"));
        assertEquals("auto bot", DetectorService.normalizeName(" Auto&nbsp; Bot "));
    }

    @Test
    public void throttlesHiscoreLookups()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;
        String name = service.updatePlayer("Lookup Me", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);

        assertTrue(service.markHiscoreLookupIfNeeded(name, config, now));
        assertFalse(service.markHiscoreLookupIfNeeded(name, config, now + 1_000L));

        service.applyHiscore(name, HiscoreProfile.error());
        assertFalse(service.markHiscoreLookupIfNeeded(name, config, now + 2_000L));
        assertTrue(service.markHiscoreLookupIfNeeded(name, config, now + config.getHiscoreCooldownMillis() + 1L));
    }

    private void recordFiveAlchs(DetectorService service, String displayName, long now, DetectorConfigSnapshot config)
    {
        for (int i = 0; i < 5; i++)
        {
            service.recordAlchObservation(displayName, "animation", 713, 100 + (i * 5), now + (i * 600L), config);
        }
    }

    private DetectorConfigSnapshot configWithClueCollectionReduction(int threshold, int penalty)
    {
        return new DetectorConfigSnapshot(
            15,
            60_000L,
            5,
            80,
            110,
            true,
            false,
            true,
            true,
            53,
            10,
            2,
            true,
            100,
            true,
            125,
            100,
            threshold,
            penalty,
            15 * 60_000L,
            IdListParser.parse("713"),
            IdListParser.parse("112,113"),
            true,
            true
        );
    }

    private DetectorConfigSnapshot configWithMagicThreshold(int magicThreshold)
    {
        return new DetectorConfigSnapshot(
            15,
            60_000L,
            5,
            80,
            110,
            true,
            false,
            true,
            true,
            magicThreshold,
            10,
            2,
            true,
            100,
            true,
            125,
            100,
            5,
            100,
            15 * 60_000L,
            IdListParser.parse("713"),
            IdListParser.parse("112,113"),
            true,
            true
        );
    }

    private DetectorConfigSnapshot configWithConfidenceThresholds(int moderateThreshold, int highThreshold)
    {
        return new DetectorConfigSnapshot(
            15,
            60_000L,
            5,
            moderateThreshold,
            highThreshold,
            true,
            false,
            true,
            true,
            53,
            10,
            2,
            true,
            100,
            true,
            125,
            100,
            5,
            100,
            15 * 60_000L,
            IdListParser.parse("713"),
            IdListParser.parse("112,113"),
            true,
            true
        );
    }

    private DetectorConfigSnapshot configWithCastAndModerateThreshold(int castThreshold, int moderateThreshold)
    {
        return new DetectorConfigSnapshot(
            15,
            60_000L,
            castThreshold,
            moderateThreshold,
            moderateThreshold + 30,
            true,
            false,
            true,
            true,
            53,
            10,
            2,
            true,
            100,
            true,
            125,
            100,
            5,
            100,
            15 * 60_000L,
            IdListParser.parse("713"),
            IdListParser.parse("112,113"),
            true,
            true
        );
    }
}
