package com.detectautoalchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.hiscore.Skill;
import org.junit.Test;

public class DetectAutoAlchersPluginTest
{
    @Test
    public void stoppingIoExecutorCancelsPollingTask() throws Exception
    {
        DetectAutoAlchersPlugin plugin = new DetectAutoAlchersPlugin();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> pollingTask = executor.scheduleWithFixedDelay(() -> { }, 1, 1, TimeUnit.HOURS);
        setField(plugin, "ioExecutor", executor);
        setField(plugin, "playerListPollTask", pollingTask);

        plugin.stopIoExecutor();

        assertTrue(pollingTask.isCancelled());
        assertTrue(executor.isShutdown());
    }

    @Test
    public void examineMenuEntryUsesSuspiciousTargetColor() throws Exception
    {
        DetectAutoAlchersPlugin plugin = new DetectAutoAlchersPlugin();
        DetectorService detectorService = new DetectorService();
        DetectorConfigSnapshot config = configWithCastAndModerateThreshold(0, 10);
        long now = 10_000L;
        String normalizedName = detectorService.updatePlayer("Auto Bot", 301, 4, StaffClassifier.STAFF_OF_FIRE, now);
        for (int index = 0; index < 5; index++)
        {
            detectorService.recordAlchObservation(
                "Auto Bot",
                "animation",
                713,
                100 + (index * 5),
                now + (index * 600L),
                config
            );
        }
        detectorService.applyHiscore(normalizedName, HiscoreProfile.found(55, 1, true));
        detectorService.recompute(config, now + 3_000L);

        List<MenuEntry> createdEntries = new ArrayList<>();
        setField(plugin, "client", testClient(createdEntries));
        setField(plugin, "config", menuDecorationConfig(false, true, new Color(144, 238, 144)));
        setField(plugin, "detectorService", detectorService);
        setField(
            plugin,
            "reportedPlayerStore",
            new ReportedPlayerStore(Files.createTempDirectory("daa-test").resolve("reported.csv"))
        );

        MenuEntry report = testMenuEntry("Report", "<col=ffffff>Auto Bot<col=ff0000> (level-48)");
        invokePrivate(
            plugin,
            "addExamineMenuEntries",
            new Class<?>[]{MenuEntry[].class, DetectorConfigSnapshot.class},
            new MenuEntry[]{report},
            config
        );

        assertEquals(1, createdEntries.size());
        assertEquals("Inspect Alch Activity", createdEntries.get(0).getOption());
        assertEquals(
            MenuHighlighter.colorTarget("Auto Bot (level-48)", MenuHighlighter.HIGH_CONFIDENCE_HIGHLIGHT_COLOR),
            createdEntries.get(0).getTarget()
        );
    }

    @Test
    public void examineMenuEntryUsesReportedTargetColor() throws Exception
    {
        DetectAutoAlchersPlugin plugin = new DetectAutoAlchersPlugin();
        Color reportedColor = new Color(144, 238, 144);
        ReportedPlayerStore reportedPlayerStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-test").resolve("reported.csv")
        );
        reportedPlayerStore.record("reported bot", "Reported Bot", Instant.EPOCH);

        List<MenuEntry> createdEntries = new ArrayList<>();
        setField(plugin, "client", testClient(createdEntries));
        setField(plugin, "config", menuDecorationConfig(false, true, reportedColor));
        setField(plugin, "detectorService", new DetectorService());
        setField(plugin, "reportedPlayerStore", reportedPlayerStore);

        MenuEntry report = testMenuEntry("Report", "<col=ffffff>Reported Bot<col=ff0000> (level-48)");
        DetectorConfigSnapshot config = configWithCastAndModerateThreshold(0, 60);
        invokePrivate(
            plugin,
            "addExamineMenuEntries",
            new Class<?>[]{MenuEntry[].class, DetectorConfigSnapshot.class},
            new MenuEntry[]{report},
            config
        );

        assertEquals(1, createdEntries.size());
        assertEquals("Inspect Alch Activity", createdEntries.get(0).getOption());
        assertEquals(
            MenuHighlighter.colorTarget("Reported Bot (level-48)", reportedColor),
            createdEntries.get(0).getTarget()
        );
    }

    @Test
    public void menuUsesMediumGreenForBotReportedByCurrentAccount() throws Exception
    {
        DetectAutoAlchersPlugin plugin = new DetectAutoAlchersPlugin();
        Color otherAccountColor = new Color(144, 238, 144);
        Color currentAccountColor = new Color(5, 75, 36);
        ReportedPlayerStore reportedPlayerStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-test").resolve("reported.csv")
        );
        reportedPlayerStore.record("reported bot", "Reported Bot", Instant.EPOCH, "42");

        setField(plugin, "client", testClient(new ArrayList<>(), 42L));
        setField(
            plugin,
            "config",
            menuDecorationConfig(false, true, true, otherAccountColor, currentAccountColor)
        );
        setField(plugin, "detectorService", new DetectorService());
        setField(plugin, "reportedPlayerStore", reportedPlayerStore);
        MenuEntry report = testMenuEntry("Report", "<col=ffffff>Reported Bot<col=ff0000> (level-48)");

        invokePrivate(
            plugin,
            "decorateMenuEntries",
            new Class<?>[]{MenuEntry[].class, DetectorConfigSnapshot.class},
            new MenuEntry[]{report},
            configWithCastAndModerateThreshold(0, 60)
        );

        assertEquals(MenuHighlighter.colorTarget("Reported Bot (level-48)", currentAccountColor), report.getTarget());
    }

    @Test
    public void persistenceDisabledKeepsReportInCurrentSessionOnly() throws Exception
    {
        DetectAutoAlchersPlugin plugin = new DetectAutoAlchersPlugin();
        Color currentAccountColor = new Color(5, 75, 36);
        ReportedPlayerStore reportedPlayerStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-test").resolve("reported.csv")
        );
        DetectorService detectorService = new DetectorService();
        setField(plugin, "client", testClient(new ArrayList<>(), 42L));
        setField(
            plugin,
            "config",
            menuDecorationConfig(false, true, false, new Color(144, 238, 144), currentAccountColor)
        );
        setField(plugin, "detectorService", detectorService);
        setField(plugin, "reportedPlayerStore", reportedPlayerStore);

        invokePrivate(
            plugin,
            "recordReportedPlayer",
            new Class<?>[]{String.class, String.class},
            "session bot",
            "Session Bot"
        );

        assertTrue(detectorService.isSuppressed("Session Bot"));
        assertFalse(reportedPlayerStore.contains("Session Bot"));
        MenuEntry report = testMenuEntry("Report", "<col=ffffff>Session Bot<col=ff0000> (level-48)");
        invokePrivate(
            plugin,
            "decorateMenuEntries",
            new Class<?>[]{MenuEntry[].class, DetectorConfigSnapshot.class},
            new MenuEntry[]{report},
            configWithCastAndModerateThreshold(0, 60)
        );
        assertEquals(MenuHighlighter.colorTarget("Session Bot (level-48)", currentAccountColor), report.getTarget());
    }

    @Test
    public void persistenceToggleAddsAndRemovesOnlySharedHistorySuppressions() throws Exception
    {
        DetectAutoAlchersPlugin plugin = new DetectAutoAlchersPlugin();
        DetectorService detectorService = new DetectorService();
        ReportedPlayerStore reportedStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-test").resolve("reported.csv")
        );
        reportedStore.record("stored bot", "Stored Bot", Instant.EPOCH, "99");
        ReportedPlayerSession session = new ReportedPlayerSession();
        session.record("42", "Session Bot");
        AtomicBoolean persist = new AtomicBoolean(false);
        setField(plugin, "config", persistenceConfig(persist));
        setField(plugin, "detectorService", detectorService);
        setField(plugin, "reportedPlayerStore", reportedStore);
        setField(plugin, "reportedPlayerSession", session);
        setField(plugin, "activeAccountUid", "42");

        invokePrivate(plugin, "syncReportedSuppressions", new Class<?>[0]);
        assertTrue(detectorService.isSuppressed("Session Bot"));
        assertFalse(detectorService.isSuppressed("Stored Bot"));

        persist.set(true);
        invokePrivate(plugin, "syncReportedSuppressions", new Class<?>[0]);
        assertTrue(detectorService.isSuppressed("Session Bot"));
        assertTrue(detectorService.isSuppressed("Stored Bot"));

        persist.set(false);
        invokePrivate(plugin, "syncReportedSuppressions", new Class<?>[0]);
        assertTrue(detectorService.isSuppressed("Session Bot"));
        assertFalse(detectorService.isSuppressed("Stored Bot"));
    }

    @Test
    public void pollingReloadsSharedListsAndReconcilesSuppressions() throws Exception
    {
        DetectAutoAlchersPlugin plugin = new DetectAutoAlchersPlugin();
        DetectorService detectorService = new DetectorService();
        Path directory = Files.createTempDirectory("daa-test");
        Path reportedPath = directory.resolve("reported.csv");
        Path watchlistPath = directory.resolve("watchlist.csv");
        Path overridePath = directory.resolve("override.csv");
        ReportedPlayerStore reportedStore = new ReportedPlayerStore(reportedPath);
        ReportedPlayerStore watchStore = new ReportedPlayerStore(watchlistPath, "date_watched");
        ReportedPlayerStore overrideStore = new ReportedPlayerStore(overridePath, "date_allowlisted");
        reportedStore.load();
        watchStore.load();
        overrideStore.load();

        setField(plugin, "client", testClient(new ArrayList<>(), 42L));
        setField(
            plugin,
            "config",
            menuDecorationConfig(false, true, true, new Color(144, 238, 144), new Color(5, 75, 36))
        );
        setField(plugin, "detectorService", detectorService);
        setField(plugin, "reportedPlayerStore", reportedStore);
        setField(plugin, "watchlistStore", watchStore);
        setField(plugin, "overrideListStore", overrideStore);

        new ReportedPlayerStore(reportedPath).record("reported bot", "Reported Bot", Instant.EPOCH, "99");
        new ReportedPlayerStore(watchlistPath, "date_watched").record("watched bot", "Watched Bot", Instant.EPOCH, "99");
        new ReportedPlayerStore(overridePath, "date_allowlisted").record("allowed bot", "Allowed Bot", Instant.EPOCH, "99");
        invokePrivate(plugin, "pollPlayerLists", new Class<?>[0]);

        assertTrue(reportedStore.contains("Reported Bot"));
        assertTrue(watchStore.contains("Watched Bot"));
        assertTrue(overrideStore.contains("Allowed Bot"));
        assertTrue(detectorService.isSuppressed("Reported Bot"));
        assertTrue(detectorService.isSuppressed("Allowed Bot"));

        new ReportedPlayerStore(reportedPath).clear();
        new ReportedPlayerStore(overridePath, "date_allowlisted").clear();
        invokePrivate(plugin, "pollPlayerLists", new Class<?>[0]);
        assertFalse(detectorService.isSuppressed("Reported Bot"));
        assertFalse(detectorService.isSuppressed("Allowed Bot"));
    }

    @Test
    public void clearingMobileSuppressionsRemovesOnlyMobileReason() throws Exception
    {
        DetectAutoAlchersPlugin plugin = new DetectAutoAlchersPlugin();
        DetectorService detectorService = new DetectorService();
        setField(plugin, "detectorService", detectorService);

        invokePrivate(plugin, "suppressMobilePlayer", new Class<?>[]{String.class}, "Mobile Alcher");
        detectorService.suppressName("Layered Mobile", SuppressionReason.REPORTED);
        invokePrivate(plugin, "suppressMobilePlayer", new Class<?>[]{String.class}, "Layered Mobile");

        assertTrue(detectorService.isSuppressed("Mobile Alcher"));
        assertTrue(detectorService.isSuppressed("Layered Mobile"));

        invokePrivate(plugin, "clearMobileSuppressions", new Class<?>[0]);

        assertFalse(detectorService.isSuppressed("Mobile Alcher"));
        assertTrue(detectorService.isSuppressed("Layered Mobile"));
    }

    @Test
    public void zeroCastThresholdStaffOnlyCandidateReachesLiveHiscoreLookupPath() throws Exception
    {
        RecordingLookupPlugin plugin = new RecordingLookupPlugin();
        DetectorService detectorService = new DetectorService();
        DetectorConfigSnapshot config = configWithCastAndModerateThreshold(0, 60);
        long now = 10_000L;
        String normalizedName = detectorService.updatePlayer(
            "Live Lookup Bot",
            301,
            4,
            StaffClassifier.STAFF_OF_FIRE,
            now
        );

        setField(plugin, "detectorService", detectorService);

        requestHiscoreIfNeeded(plugin, "Live Lookup Bot", normalizedName, config, now);

        assertEquals(1, plugin.lookupCount);
        assertEquals("Live Lookup Bot", plugin.lookupName);
    }

    @Test
    public void liveHiscoreLookupRetriesAfterTimeoutAndAppliesSecondResult() throws Exception
    {
        ScriptedLookupPlugin plugin = new ScriptedLookupPlugin();
        DetectorService detectorService = new DetectorService();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        DetectorConfigSnapshot config = configWithCastAndModerateThreshold(0, 60);
        long now = 10_000L;
        String normalizedName = detectorService.updatePlayer(
            "Retry Lookup Bot",
            301,
            4,
            StaffClassifier.STAFF_OF_FIRE,
            now
        );

        setField(plugin, "detectorService", detectorService);
        setField(plugin, "ioExecutor", executor);
        try
        {
            requestHiscoreIfNeeded(plugin, "Retry Lookup Bot", normalizedName, config, now);

            waitUntil(() -> plugin.lookupCount == 2);
            plugin.futures.get(1).complete(hiscoreResult(55));
            waitUntil(() -> hasHiscoreStatus(detectorService, normalizedName, "found"));

            assertEquals(2, plugin.lookupCount);
            assertEquals(0, detectorService.getHiscoreLookupsInFlight());
            assertEquals("found", result(detectorService, normalizedName).getHiscoreStatus());
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    public void liveHiscoreLookupTimeoutExhaustionClearsPendingState() throws Exception
    {
        ScriptedLookupPlugin plugin = new ScriptedLookupPlugin();
        DetectorService detectorService = new DetectorService();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        DetectorConfigSnapshot config = configWithCastAndModerateThreshold(0, 60);
        long now = 10_000L;
        String normalizedName = detectorService.updatePlayer(
            "Hung Lookup Bot",
            301,
            4,
            StaffClassifier.STAFF_OF_FIRE,
            now
        );

        setField(plugin, "detectorService", detectorService);
        setField(plugin, "ioExecutor", executor);
        try
        {
            requestHiscoreIfNeeded(plugin, "Hung Lookup Bot", normalizedName, config, now);

            waitUntil(() -> hasHiscoreStatus(detectorService, normalizedName, "error"));

            assertEquals(2, plugin.lookupCount);
            assertEquals(0, detectorService.getHiscoreLookupsInFlight());
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    public void removeReportedWatchlistRemovesReportedIntersection() throws Exception
    {
        DetectAutoAlchersPlugin plugin = new DetectAutoAlchersPlugin();
        ReportedPlayerStore reportedPlayerStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-test").resolve("reported.csv")
        );
        ReportedPlayerStore watchlistStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-test").resolve("watchlist.csv")
        );

        reportedPlayerStore.record("reported bot", "Reported Bot", Instant.EPOCH);
        watchlistStore.record("reported bot", "Reported Bot", Instant.EPOCH);
        watchlistStore.record("fresh bot", "Fresh Bot", Instant.EPOCH);
        setField(plugin, "reportedPlayerStore", reportedPlayerStore);
        setField(plugin, "watchlistStore", watchlistStore);

        invokePrivate(plugin, "removeReportedWatchlist", new Class<?>[0]);

        assertFalse(watchlistStore.contains("Reported Bot"));
        assertTrue(watchlistStore.contains("Fresh Bot"));
    }

    @Test
    public void removeBannedWatchlistRemovesOnlyHiscoreNotFoundEntries() throws Exception
    {
        NamedLookupPlugin plugin = new NamedLookupPlugin();
        ReportedPlayerStore watchlistStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-test").resolve("watchlist.csv")
        );

        watchlistStore.record("gone bot", "Gone Bot", Instant.EPOCH);
        watchlistStore.record("found bot", "Found Bot", Instant.EPOCH);
        watchlistStore.record("error bot", "Error Bot", Instant.EPOCH);
        plugin.results.put("Gone Bot", CompletableFuture.completedFuture(null));
        plugin.results.put("Found Bot", CompletableFuture.completedFuture(hiscoreResult(55)));
        plugin.results.put("Error Bot", CompletableFuture.failedFuture(new RuntimeException("lookup failed")));
        setField(plugin, "watchlistStore", watchlistStore);

        invokePrivate(plugin, "removeBannedWatchlist", new Class<?>[0]);

        assertFalse(watchlistStore.contains("Gone Bot"));
        assertTrue(watchlistStore.contains("Found Bot"));
        assertTrue(watchlistStore.contains("Error Bot"));
        assertEquals(3, plugin.lookupCount);
    }

    @Test
    public void removeBannedWatchlistKeepsTimeoutEntries() throws Exception
    {
        PendingLookupPlugin plugin = new PendingLookupPlugin();
        ReportedPlayerStore watchlistStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-test").resolve("watchlist.csv")
        );

        watchlistStore.record("slow bot", "Slow Bot", Instant.EPOCH);
        setField(plugin, "watchlistStore", watchlistStore);

        invokePrivate(plugin, "removeBannedWatchlist", new Class<?>[0]);
        waitUntil(() -> !watchlistCleanupInFlight(plugin));

        assertTrue(watchlistStore.contains("Slow Bot"));
        assertEquals(1, plugin.lookupCount);
    }

    @Test
    public void removeBannedWatchlistIgnoresOverlappingScans() throws Exception
    {
        PendingLookupPlugin plugin = new PendingLookupPlugin();
        ReportedPlayerStore watchlistStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-test").resolve("watchlist.csv")
        );

        watchlistStore.record("slow bot", "Slow Bot", Instant.EPOCH);
        setField(plugin, "watchlistStore", watchlistStore);

        invokePrivate(plugin, "removeBannedWatchlist", new Class<?>[0]);
        invokePrivate(plugin, "removeBannedWatchlist", new Class<?>[0]);
        plugin.future.complete(hiscoreResult(55));
        waitUntil(() -> !watchlistCleanupInFlight(plugin));

        assertTrue(watchlistStore.contains("Slow Bot"));
        assertEquals(1, plugin.lookupCount);
    }

    @Test
    public void removeBannedWatchlistUpdatesProgressUntilCompletion() throws Exception
    {
        ScriptedLookupPlugin plugin = new ScriptedLookupPlugin();
        ReportedPlayerStore watchlistStore = new ReportedPlayerStore(
            Files.createTempDirectory("daa-test").resolve("watchlist.csv")
        );

        watchlistStore.record("found bot", "Found Bot", Instant.EPOCH);
        watchlistStore.record("error bot", "Error Bot", Instant.EPOCH);
        watchlistStore.record("gone bot", "Gone Bot", Instant.EPOCH);
        setField(plugin, "watchlistStore", watchlistStore);

        invokePrivate(plugin, "removeBannedWatchlist", new Class<?>[0]);
        waitUntil(() -> cleanupStatus(plugin, "found bot") == WatchlistCleanupProgress.Status.CHECKING);

        WatchlistCleanupProgress progress = watchlistCleanupProgress(plugin);
        assertEquals(3, progress.getTotal());
        assertEquals(0, progress.getChecked());
        assertEquals("Found Bot", progress.getCurrentDisplayName());
        assertEquals(WatchlistCleanupProgress.Status.PENDING, cleanupStatus(plugin, "error bot"));

        plugin.futures.get(0).complete(hiscoreResult(55));
        waitUntil(() -> plugin.lookupCount == 2
            && cleanupStatus(plugin, "found bot") == WatchlistCleanupProgress.Status.FOUND
            && cleanupStatus(plugin, "error bot") == WatchlistCleanupProgress.Status.CHECKING);

        progress = watchlistCleanupProgress(plugin);
        assertEquals(1, progress.getChecked());
        assertEquals("Error Bot", progress.getCurrentDisplayName());

        plugin.futures.get(1).completeExceptionally(new RuntimeException("lookup failed"));
        waitUntil(() -> plugin.lookupCount == 3
            && cleanupStatus(plugin, "error bot") == WatchlistCleanupProgress.Status.ERROR
            && cleanupStatus(plugin, "gone bot") == WatchlistCleanupProgress.Status.CHECKING);

        CountDownLatch releaseFinish = new CountDownLatch(1);
        CountDownLatch finishBlocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() ->
        {
            finishBlocked.countDown();
            try
            {
                releaseFinish.await(2, TimeUnit.SECONDS);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(finishBlocked.await(1, TimeUnit.SECONDS));
        setField(plugin, "ioExecutor", executor);

        try
        {
            plugin.futures.get(2).complete(null);
            waitUntil(() -> cleanupStatus(plugin, "gone bot") == WatchlistCleanupProgress.Status.NOT_FOUND);

            progress = watchlistCleanupProgress(plugin);
            assertEquals(3, progress.getChecked());
            assertEquals("", progress.getCurrentDisplayName());
            assertTrue(watchlistStore.contains("Gone Bot"));

            releaseFinish.countDown();
            waitUntil(() -> !watchlistCleanupInFlight(plugin));

            assertTrue(watchlistCleanupProgress(plugin) == null);
            assertTrue(watchlistStore.contains("Found Bot"));
            assertTrue(watchlistStore.contains("Error Bot"));
            assertFalse(watchlistStore.contains("Gone Bot"));
        }
        finally
        {
            releaseFinish.countDown();
            executor.shutdownNow();
        }
    }

    private static void requestHiscoreIfNeeded(
        DetectAutoAlchersPlugin plugin,
        String displayName,
        String normalizedName,
        DetectorConfigSnapshot config,
        long nowMillis) throws Exception
    {
        Method method = DetectAutoAlchersPlugin.class.getDeclaredMethod(
            "requestHiscoreIfNeeded",
            String.class,
            String.class,
            DetectorConfigSnapshot.class,
            long.class
        );
        method.setAccessible(true);
        try
        {
            method.invoke(plugin, displayName, normalizedName, config, nowMillis);
        }
        catch (InvocationTargetException ex)
        {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception)
            {
                throw (Exception) cause;
            }
            throw ex;
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = DetectAutoAlchersPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invokePrivate(
        DetectAutoAlchersPlugin plugin,
        String name,
        Class<?>[] parameterTypes,
        Object... args) throws Exception
    {
        Method method = DetectAutoAlchersPlugin.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try
        {
            return method.invoke(plugin, args);
        }
        catch (InvocationTargetException ex)
        {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception)
            {
                throw (Exception) cause;
            }
            throw ex;
        }
    }

    private static DetectorConfigSnapshot configWithCastAndModerateThreshold(int castThreshold, int moderateThreshold)
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

    private static SuspicionResult result(DetectorService detectorService, String normalizedName)
    {
        return detectorService.getResultsByName(Set.of(normalizedName)).get(normalizedName);
    }

    private static boolean hasHiscoreStatus(DetectorService detectorService, String normalizedName, String status)
    {
        SuspicionResult result = result(detectorService, normalizedName);
        return result != null && status.equals(result.getHiscoreStatus());
    }

    private static boolean watchlistCleanupInFlight(DetectAutoAlchersPlugin plugin)
    {
        try
        {
            Field field = DetectAutoAlchersPlugin.class.getDeclaredField("watchlistHiscoreCleanupInFlight");
            field.setAccessible(true);
            return ((AtomicBoolean) field.get(plugin)).get();
        }
        catch (ReflectiveOperationException ex)
        {
            throw new AssertionError(ex);
        }
    }

    private static WatchlistCleanupProgress watchlistCleanupProgress(DetectAutoAlchersPlugin plugin)
    {
        try
        {
            Field field = DetectAutoAlchersPlugin.class.getDeclaredField("watchlistCleanupProgress");
            field.setAccessible(true);
            return (WatchlistCleanupProgress) field.get(plugin);
        }
        catch (ReflectiveOperationException ex)
        {
            throw new AssertionError(ex);
        }
    }

    private static WatchlistCleanupProgress.Status cleanupStatus(DetectAutoAlchersPlugin plugin, String normalizedName)
    {
        WatchlistCleanupProgress progress = watchlistCleanupProgress(plugin);
        return progress == null ? null : progress.getStatus(normalizedName);
    }

    private static Client testClient(List<MenuEntry> createdEntries)
    {
        return testClient(createdEntries, 0L);
    }

    private static Client testClient(List<MenuEntry> createdEntries, long accountHash)
    {
        return (Client) Proxy.newProxyInstance(
            Client.class.getClassLoader(),
            new Class<?>[]{Client.class},
            (proxy, method, args) -> {
                if ("createMenuEntry".equals(method.getName()))
                {
                    MenuEntry entry = testMenuEntry("", "");
                    createdEntries.add(entry);
                    return entry;
                }
                if ("getAccountHash".equals(method.getName()))
                {
                    return accountHash;
                }
                return defaultValue(method.getReturnType());
            }
        );
    }

    private static DetectAutoAlchersConfig menuDecorationConfig(
        boolean showMenuDetectionScores,
        boolean colorMenuEntries,
        Color reportedColor)
    {
        return menuDecorationConfig(
            showMenuDetectionScores,
            colorMenuEntries,
            true,
            reportedColor,
            new Color(5, 75, 36)
        );
    }

    private static DetectAutoAlchersConfig persistenceConfig(AtomicBoolean persist)
    {
        return (DetectAutoAlchersConfig) Proxy.newProxyInstance(
            DetectAutoAlchersConfig.class.getClassLoader(),
            new Class<?>[]{DetectAutoAlchersConfig.class},
            (proxy, method, args) -> "persistReportedPlayers".equals(method.getName())
                ? persist.get()
                : defaultValue(method.getReturnType())
        );
    }

    private static DetectAutoAlchersConfig menuDecorationConfig(
        boolean showMenuDetectionScores,
        boolean colorMenuEntries,
        boolean persistReportedPlayers,
        Color reportedColor,
        Color currentAccountReportedColor)
    {
        return (DetectAutoAlchersConfig) Proxy.newProxyInstance(
            DetectAutoAlchersConfig.class.getClassLoader(),
            new Class<?>[]{DetectAutoAlchersConfig.class},
            (proxy, method, args) -> {
                switch (method.getName())
                {
                    case "showMenuDetectionScores":
                        return showMenuDetectionScores;
                    case "colorMenuEntries":
                        return colorMenuEntries;
                    case "reportedPlayerHighlightColor":
                        return reportedColor;
                    case "currentAccountReportedHighlightColor":
                        return currentAccountReportedColor;
                    case "persistReportedPlayers":
                        return persistReportedPlayers;
                    default:
                        return defaultValue(method.getReturnType());
                }
            }
        );
    }

    private static MenuEntry testMenuEntry(String option, String target)
    {
        String[] values = {option, target};
        return (MenuEntry) Proxy.newProxyInstance(
            MenuEntry.class.getClassLoader(),
            new Class<?>[]{MenuEntry.class},
            (proxy, method, args) -> {
                switch (method.getName())
                {
                    case "getOption":
                        return values[0];
                    case "setOption":
                        values[0] = (String) args[0];
                        return proxy;
                    case "getTarget":
                        return values[1];
                    case "setTarget":
                        values[1] = (String) args[0];
                        return proxy;
                    case "getPlayer":
                        return null;
                    case "setType":
                    case "onClick":
                        return proxy;
                    case "toString":
                        return values[0] + " " + values[1];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        return defaultValue(method.getReturnType());
                }
            }
        );
    }

    private static Object defaultValue(Class<?> returnType)
    {
        if (!returnType.isPrimitive())
        {
            return null;
        }
        if (returnType == boolean.class)
        {
            return false;
        }
        if (returnType == void.class)
        {
            return null;
        }
        if (returnType == char.class)
        {
            return '\0';
        }
        if (returnType == long.class)
        {
            return 0L;
        }
        if (returnType == float.class)
        {
            return 0F;
        }
        if (returnType == double.class)
        {
            return 0D;
        }
        if (returnType == byte.class)
        {
            return (byte) 0;
        }
        if (returnType == short.class)
        {
            return (short) 0;
        }
        return 0;
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + 2_000L;
        while (System.currentTimeMillis() < deadline)
        {
            if (condition.getAsBoolean())
            {
                return;
            }
            Thread.sleep(10L);
        }
        fail("Condition was not met before timeout");
    }

    private static HiscoreResult hiscoreResult(int magicLevel)
    {
        EnumMap<HiscoreSkill, Skill> skills = new EnumMap<>(HiscoreSkill.class);
        for (HiscoreSkill hiscoreSkill : HiscoreSkill.values())
        {
            if (hiscoreSkill.getType() == HiscoreSkillType.SKILL)
            {
                skills.put(hiscoreSkill, new Skill(1, hiscoreSkill == HiscoreSkill.MAGIC ? magicLevel : 1, 0));
            }
        }
        skills.put(HiscoreSkill.CLUE_SCROLL_ALL, new Skill(-1, -1, -1));
        skills.put(HiscoreSkill.COLLECTIONS_LOGGED, new Skill(-1, -1, -1));
        return new HiscoreResult("player", skills);
    }

    private static final class NamedLookupPlugin extends DetectAutoAlchersPlugin
    {
        private final Map<String, CompletableFuture<HiscoreResult>> results = new HashMap<>();
        private int lookupCount;

        @Override
        CompletableFuture<HiscoreResult> lookupHiscoreAsync(String displayName)
        {
            lookupCount++;
            CompletableFuture<HiscoreResult> result = results.get(displayName);
            return result == null ? CompletableFuture.completedFuture(hiscoreResult(55)) : result;
        }
    }

    private static final class PendingLookupPlugin extends DetectAutoAlchersPlugin
    {
        private final CompletableFuture<HiscoreResult> future = new CompletableFuture<>();
        private int lookupCount;

        @Override
        CompletableFuture<HiscoreResult> lookupHiscoreAsync(String displayName)
        {
            lookupCount++;
            return future;
        }

        @Override
        long hiscoreLookupTimeoutMillis()
        {
            return 50L;
        }

        @Override
        long hiscoreLookupRetryDelayMillis()
        {
            return 5L;
        }
    }

    private static final class RecordingLookupPlugin extends DetectAutoAlchersPlugin
    {
        private final CompletableFuture<HiscoreResult> future = new CompletableFuture<>();
        private int lookupCount;
        private String lookupName;

        @Override
        CompletableFuture<HiscoreResult> lookupHiscoreAsync(String displayName)
        {
            lookupCount++;
            lookupName = displayName;
            return future;
        }
    }

    private static final class ScriptedLookupPlugin extends DetectAutoAlchersPlugin
    {
        private final List<CompletableFuture<HiscoreResult>> futures = new ArrayList<>();
        private int lookupCount;

        @Override
        CompletableFuture<HiscoreResult> lookupHiscoreAsync(String displayName)
        {
            lookupCount++;
            CompletableFuture<HiscoreResult> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        }

        @Override
        long hiscoreLookupTimeoutMillis()
        {
            return 100L;
        }

        @Override
        long hiscoreLookupRetryDelayMillis()
        {
            return 5L;
        }
    }
}
