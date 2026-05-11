package com.detectautoalchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
        assertEquals(120, suspects.get(0).getScoreBreakdown().getPositiveTotal());
        assertEquals(0, suspects.get(0).getScoreBreakdown().getPenaltyTotal());
        assertEquals(120, suspects.get(0).getScoreBreakdown().getFinalTotal());
        assertTrue(suspects.get(0).getScoreBreakdown().getScoreLabels().contains("staff +30"));
        assertTrue(suspects.get(0).getScoreBreakdown().getScoreLabels().contains("casts +50"));
        assertTrue(suspects.get(0).getScoreBreakdown().getScoreLabels().contains("cadence +10"));
        assertTrue(suspects.get(0).getScoreBreakdown().getScoreLabels().contains("magic profile +30"));
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
    public void clueCollectionAtThresholdPenalizes()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = configWithClueCollectionReduction(4, 40);
        long now = 10_000L;

        String name = service.updatePlayer("Four Clues", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Four Clues", now, config);
        service.applyHiscore(name, HiscoreProfile.found(89, 2, 40, 2, 2, true));

        service.recompute(config, now + 3_000L);
        List<SuspicionResult> suspects = service.getSuspiciousResults();

        assertEquals(1, suspects.size());
        assertTrue(suspects.get(0).isClueCollectionActivitySuppressed());
        assertEquals(80, suspects.get(0).getScore());
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
        assertTrue(suspects.get(0).getScoreBreakdown().getScoreLabels().contains("99 magic +100"));
        assertTrue(suspects.get(0).getScoreBreakdown().getScoreLabels().contains("non-magic total level -100"));
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
    public void suppressionReasonsDoNotClearEachOther()
    {
        DetectorService service = new DetectorService();

        service.suppressName("Layered Alcher", SuppressionReason.MOBILE);
        service.suppressName("Layered Alcher", SuppressionReason.REPORTED);

        service.unsuppressNames(Set.of("layered alcher"), SuppressionReason.MOBILE);
        assertTrue(service.isSuppressed("Layered Alcher"));

        service.unsuppressNames(Set.of("layered alcher"), SuppressionReason.REPORTED);
        assertFalse(service.isSuppressed("Layered Alcher"));
    }

    @Test
    public void syncSuppressionReasonRemovesStaleReasonOnly()
    {
        DetectorService service = new DetectorService();

        service.suppressName("Ignored Alcher", SuppressionReason.RUNELITE_IGNORE);
        service.suppressName("Reported Alcher", SuppressionReason.REPORTED);
        service.syncSuppressionReason(Set.of("fresh ignored"), SuppressionReason.RUNELITE_IGNORE);

        assertFalse(service.isSuppressed("Ignored Alcher"));
        assertTrue(service.isSuppressed("Reported Alcher"));
        assertTrue(service.isSuppressed("Fresh Ignored"));
    }

    @Test
    public void hiscoreProfileRecomputesNonMagicThresholdFromStoredLevels()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot strictConfig = configWithNonMagicThreshold(50);
        DetectorConfigSnapshot relaxedConfig = configWithNonMagicThreshold(70);
        long now = 10_000L;

        String name = service.updatePlayer("Threshold Alcher", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        recordFiveAlchs(service, "Threshold Alcher", now, strictConfig);
        service.applyHiscore(
            name,
            HiscoreProfile.found(89, 3, 180, 0, 0, false, new int[]{60, 60, 60})
        );

        service.recompute(strictConfig, now + 3_000L);
        assertEquals(90, service.getScoresByName().get("threshold alcher").intValue());

        service.recompute(relaxedConfig, now + 3_000L);
        List<SuspicionResult> suspects = service.getSuspiciousResults();

        assertEquals(1, suspects.size());
        assertTrue(suspects.get(0).isMagicDominant());
        assertEquals(0, suspects.get(0).getNonMagicSkillsAboveThreshold());
        assertEquals(120, suspects.get(0).getScore());
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
        service.recordAlchObservation("Lookup Me", "animation", 713, 100, now, config);

        assertTrue(service.markHiscoreLookupIfNeeded(name, config, now));
        assertFalse(service.markHiscoreLookupIfNeeded(name, config, now + 1_000L));

        service.applyHiscore(name, HiscoreProfile.error());
        assertFalse(service.markHiscoreLookupIfNeeded(name, config, now + 2_000L));
        service.recordAlchObservation(
            "Lookup Me",
            "animation",
            713,
            200,
            now + config.getHiscoreCooldownMillis(),
            config
        );
        assertTrue(service.markHiscoreLookupIfNeeded(name, config, now + config.getHiscoreCooldownMillis() + 1L));
    }

    @Test
    public void staffOnlyDoesNotTriggerHiscoreLookup()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;
        String name = service.updatePlayer("Staff Only Lookup", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);

        assertFalse(service.markHiscoreLookupIfNeeded(name, config, now));
    }

    @Test
    public void zeroCastThresholdStaffOnlyTriggersHiscoreLookupAndScoreOnlyDetection()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = configWithCastAndModerateThreshold(0, 60);
        long now = 10_000L;
        String name = service.updatePlayer("Lookup Score Bot", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);

        assertTrue(service.markHiscoreLookupIfNeeded(name, config, now));

        service.applyHiscore(name, HiscoreProfile.found(55, 1, true));
        service.recompute(config, now + 3_000L);
        List<SuspicionResult> suspects = service.getSuspiciousResults();

        assertEquals(1, suspects.size());
        assertEquals("Lookup Score Bot", suspects.get(0).getDisplayName());
        assertEquals(60, suspects.get(0).getScore());
        assertEquals(0, suspects.get(0).getCastCount());
        assertFalse(suspects.get(0).isBehaviorMatch());
        assertTrue(suspects.get(0).isMagicDominant());
    }

    @Test
    public void singleObservationWithoutStaffDoesNotTriggerHiscoreLookup()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;
        String name = service.updatePlayer("One Cast No Staff", 301, 4, -1, now);
        service.recordAlchObservation("One Cast No Staff", "animation", 713, 100, now, config);

        assertFalse(service.markHiscoreLookupIfNeeded(name, config, now));
    }

    @Test
    public void staffWithSingleObservationTriggersHiscoreLookup()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;
        String name = service.updatePlayer("Staff One Cast", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        service.recordAlchObservation("Staff One Cast", "animation", 713, 100, now, config);

        assertTrue(service.markHiscoreLookupIfNeeded(name, config, now));
    }

    @Test
    public void repeatedBehaviorWithoutStaffTriggersHiscoreLookup()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;
        String name = service.updatePlayer("Repeated No Staff", 301, 4, -1, now);
        recordFiveAlchs(service, "Repeated No Staff", now, config);

        assertTrue(service.markHiscoreLookupIfNeeded(name, config, now));
    }

    @Test
    public void maxHiscoreLookupCapacityPreventsFourthConcurrentLookup()
    {
        DetectorService service = new DetectorService();
        DetectorConfigSnapshot config = DetectorConfigSnapshot.defaultsForTesting();
        long now = 10_000L;
        String first = addLookupCandidate(service, "Lookup One", now, config);
        String second = addLookupCandidate(service, "Lookup Two", now, config);
        String third = addLookupCandidate(service, "Lookup Three", now, config);
        String fourth = addLookupCandidate(service, "Lookup Four", now, config);

        assertTrue(service.markHiscoreLookupIfNeeded(first, config, now));
        assertTrue(service.markHiscoreLookupIfNeeded(second, config, now));
        assertTrue(service.markHiscoreLookupIfNeeded(third, config, now));
        assertEquals(3, service.getHiscoreLookupsInFlight());
        assertFalse(service.markHiscoreLookupIfNeeded(fourth, config, now));

        service.applyHiscore(first, HiscoreProfile.found(55, 1, true));

        assertEquals(2, service.getHiscoreLookupsInFlight());
        assertTrue(service.markHiscoreLookupIfNeeded(fourth, config, now));
    }

    private void recordFiveAlchs(DetectorService service, String displayName, long now, DetectorConfigSnapshot config)
    {
        for (int i = 0; i < 5; i++)
        {
            service.recordAlchObservation(displayName, "animation", 713, 100 + (i * 5), now + (i * 600L), config);
        }
    }

    private String addLookupCandidate(DetectorService service, String displayName, long now, DetectorConfigSnapshot config)
    {
        String name = service.updatePlayer(displayName, 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        service.recordAlchObservation(displayName, "animation", 713, 100, now, config);
        return name;
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
            4,
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
            4,
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
            4,
            100,
            15 * 60_000L,
            IdListParser.parse("713"),
            IdListParser.parse("112,113"),
            true,
            true
        );
    }

    private DetectorConfigSnapshot configWithNonMagicThreshold(int nonMagicThreshold)
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
            nonMagicThreshold,
            2,
            true,
            100,
            false,
            125,
            100,
            4,
            100,
            15 * 60_000L,
            IdListParser.parse("713"),
            IdListParser.parse("112,113"),
            true,
            true
        );
    }
}
