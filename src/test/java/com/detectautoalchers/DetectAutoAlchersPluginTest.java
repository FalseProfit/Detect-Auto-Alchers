package com.detectautoalchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.hiscore.Skill;
import org.junit.Test;

public class DetectAutoAlchersPluginTest
{
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
