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
}
