package com.detectautoalchers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public class PanelRefreshGateTest
{
    @Test
    public void initialEmptyStateRefreshes()
    {
        PanelRefreshGate gate = new PanelRefreshGate(5_000L);

        assertTrue(gate.shouldRefresh(Collections.emptyList(), 10_000L, false));
    }

    @Test
    public void unchangedSuspectStateDoesNotRefreshBeforeInterval()
    {
        PanelRefreshGate gate = new PanelRefreshGate(5_000L);
        SuspicionResult result = result("refresh alcher", 120, DetectionConfidence.HIGH, 5);

        assertTrue(gate.shouldRefresh(Collections.singletonList(result), 10_000L, false));
        assertFalse(gate.shouldRefresh(Collections.singletonList(result), 11_000L, false));
    }

    @Test
    public void changedSuspectStateRefreshesImmediately()
    {
        PanelRefreshGate gate = new PanelRefreshGate(5_000L);

        assertTrue(gate.shouldRefresh(
            Collections.singletonList(result("refresh alcher", 120, DetectionConfidence.HIGH, 5)),
            10_000L,
            false
        ));
        assertTrue(gate.shouldRefresh(
            Collections.singletonList(result("refresh alcher", 130, DetectionConfidence.HIGH, 6)),
            11_000L,
            false
        ));
    }

    @Test
    public void fallbackIntervalRefreshesUnchangedState()
    {
        PanelRefreshGate gate = new PanelRefreshGate(5_000L);
        SuspicionResult result = result("refresh alcher", 120, DetectionConfidence.HIGH, 5);

        assertTrue(gate.shouldRefresh(Collections.singletonList(result), 10_000L, false));
        assertTrue(gate.shouldRefresh(Collections.singletonList(result), 15_000L, false));
    }

    private SuspicionResult result(String name, int score, DetectionConfidence confidence, int casts)
    {
        return new SuspicionResult(
            name,
            "Refresh Alcher",
            score,
            confidence,
            casts,
            true,
            true,
            true,
            false,
            true,
            89,
            1,
            40,
            false,
            1,
            2,
            3,
            false,
            "found",
            431,
            2,
            StaffClassifier.STAFF_OF_FIRE,
            1_000L,
            new ScoreBreakdown(
                30,
                50,
                30,
                0,
                10,
                0,
                0,
                true,
                true,
                true
            )
        );
    }
}
