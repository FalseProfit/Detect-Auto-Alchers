package com.detectautoalchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.time.Instant;
import net.runelite.api.Client;
import org.junit.Test;

public class DetectAutoAlchersOverlayTest
{
    private static final Color LIGHT_GREEN = new Color(144, 238, 144);
    private static final Color CURRENT_ACCOUNT_GREEN = new Color(5, 75, 36);

    @Test
    public void distinguishesCurrentAccountFromOtherAndLegacyReports() throws Exception
    {
        ReportedPlayerStore reportedStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-overlay").resolve("reported.csv")
        );
        reportedStore.record("mine bot", "Mine Bot", Instant.EPOCH, "42");
        reportedStore.record("other bot", "Other Bot", Instant.EPOCH, "99");
        reportedStore.record("legacy bot", "Legacy Bot", Instant.EPOCH);

        DetectAutoAlchersOverlay overlay = overlay(reportedStore, new ReportedPlayerSession(), true, 42L);

        assertEquals(CURRENT_ACCOUNT_GREEN, overlay.reportedColorFor("Mine Bot"));
        assertEquals(LIGHT_GREEN, overlay.reportedColorFor("Other Bot"));
        assertEquals(LIGHT_GREEN, overlay.reportedColorFor("Legacy Bot"));
    }

    @Test
    public void disabledPersistenceUsesSessionReportsButNotStoredHistory() throws Exception
    {
        ReportedPlayerStore reportedStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-overlay").resolve("reported.csv")
        );
        reportedStore.record("stored bot", "Stored Bot", Instant.EPOCH, "42");
        ReportedPlayerSession session = new ReportedPlayerSession();
        session.record("42", "Session Bot");

        DetectAutoAlchersOverlay overlay = overlay(reportedStore, session, false, 42L);

        assertEquals(CURRENT_ACCOUNT_GREEN, overlay.reportedColorFor("Session Bot"));
        assertNull(overlay.reportedColorFor("Stored Bot"));
    }

    private static DetectAutoAlchersOverlay overlay(
        ReportedPlayerStore reportedStore,
        ReportedPlayerSession session,
        boolean persist,
        long accountHash) throws Exception
    {
        ReportedPlayerStore watchStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-overlay").resolve("watch.csv"),
            "date_watched"
        );
        return new DetectAutoAlchersOverlay(
            client(accountHash),
            new DetectorService(),
            reportedStore,
            watchStore,
            session,
            config(persist),
            null
        );
    }

    private static Client client(long accountHash)
    {
        return (Client) Proxy.newProxyInstance(
            Client.class.getClassLoader(),
            new Class<?>[]{Client.class},
            (proxy, method, args) -> "getAccountHash".equals(method.getName()) ? accountHash : defaultValue(method.getReturnType())
        );
    }

    private static DetectAutoAlchersConfig config(boolean persist)
    {
        return (DetectAutoAlchersConfig) Proxy.newProxyInstance(
            DetectAutoAlchersConfig.class.getClassLoader(),
            new Class<?>[]{DetectAutoAlchersConfig.class},
            (proxy, method, args) -> {
                switch (method.getName())
                {
                    case "highlightReportedPlayers":
                        return true;
                    case "persistReportedPlayers":
                        return persist;
                    case "reportedPlayerHighlightColor":
                        return LIGHT_GREEN;
                    case "currentAccountReportedHighlightColor":
                        return CURRENT_ACCOUNT_GREEN;
                    default:
                        return defaultValue(method.getReturnType());
                }
            }
        );
    }

    private static Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive())
        {
            return null;
        }
        if (type == boolean.class)
        {
            return false;
        }
        if (type == long.class)
        {
            return 0L;
        }
        if (type == int.class)
        {
            return 0;
        }
        return null;
    }
}
