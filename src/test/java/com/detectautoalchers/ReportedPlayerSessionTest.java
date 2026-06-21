package com.detectautoalchers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReportedPlayerSessionTest
{
    @Test
    public void reportsRemainScopedToTheirAccountUid()
    {
        ReportedPlayerSession session = new ReportedPlayerSession();

        session.record("101", "Session Bot");

        assertTrue(session.contains("101", "Session Bot"));
        assertFalse(session.contains("202", "Session Bot"));
    }

    @Test
    public void unavailableUidCanBeAssociatedAfterLoginCompletes()
    {
        ReportedPlayerSession session = new ReportedPlayerSession();
        session.record(null, "Early Bot");

        session.associateUnattributedReports("101");

        assertTrue(session.contains("101", "Early Bot"));
        assertFalse(session.contains(null, "Early Bot"));
    }
}
