package com.detectautoalchers;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import net.runelite.client.hiscore.HiscoreResult;
import org.junit.Test;

public class DetectAutoAlchersPluginTest
{
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
}
