package com.detectautoalchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
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
        assertEquals(120, suspects.get(0).getScore());
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
            true,
            false,
            true,
            21,
            10,
            2,
            true,
            99,
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
}
