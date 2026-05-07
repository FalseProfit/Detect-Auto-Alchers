package com.detectautoalchers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.MenuEntry;
import org.junit.Test;

public class MenuHighlighterTest
{
    @Test
    public void matchesTaggedPlayerTargets()
    {
        Set<String> suspects = Collections.singleton("auto bot");

        assertTrue(MenuHighlighter.shouldHighlightTarget("<col=ffffff>Auto Bot<col=ffff00> (level-3)", suspects));
        assertFalse(MenuHighlighter.shouldHighlightTarget("<col=ffffff>Auto Bottle<col=ffff00> (level-3)", suspects));
        assertEquals("auto bot", MenuHighlighter.findMatchingSuspiciousName("<col=ffffff>Auto Bot<col=ffff00> (level-3)", suspects));
    }

    @Test
    public void wrapsTextInColorTag()
    {
        String highlighted = MenuHighlighter.colorText("Report");

        assertTrue(highlighted.contains("<col="));
        assertTrue(highlighted.contains("Report"));
    }

    @Test
    public void colorsPlayerTargetWithoutDroppingTrailingMobileIcon()
    {
        String highlighted = MenuHighlighter.colorTarget("<col=ffffff>Auto Bot<col=ff0000> (level-3)<img=23>");

        assertTrue(highlighted.contains("<col="));
        assertTrue(highlighted.contains("Auto Bot (level-3)"));
        assertTrue(highlighted.endsWith("<img=23>"));
    }

    @Test
    public void reportedPlayerColorOverridesSuspiciousColor()
    {
        Color reportedColor = new Color(144, 238, 144);
        MenuEntry entry = testMenuEntry("Report", "<col=ffffff>Auto Bot<col=ff0000> (level-3)");

        MenuHighlighter.highlight(
            new MenuEntry[]{entry},
            Collections.singletonMap("auto bot", DetectionConfidence.HIGH),
            Collections.singleton("auto bot"),
            reportedColor
        );

        assertEquals(MenuHighlighter.colorTarget("Auto Bot (level-3)", reportedColor), entry.getTarget());
        assertEquals(MenuHighlighter.colorText("Report", reportedColor), entry.getOption());
    }

    @Test
    public void sortsHighConfidencePlayersLastInMenuArrayForVisibleTopOrder()
    {
        Map<String, DetectionConfidence> confidenceByName = new LinkedHashMap<>();
        confidenceByName.put("high bot", DetectionConfidence.HIGH);
        confidenceByName.put("maybe bot", DetectionConfidence.MODERATE);
        MenuEntry walkHere = testMenuEntry("Walk here", "");
        MenuEntry unflagged = testMenuEntry("Report", "<col=ffffff>Regular Player<col=ff0000> (level-3)");
        MenuEntry moderate = testMenuEntry("Report", "<col=ffffff>Maybe Bot<col=ff0000> (level-3)");
        MenuEntry high = testMenuEntry("Report", "<col=ffffff>High Bot<col=ff0000> (level-3)");
        MenuEntry[] entries = {high, unflagged, moderate, walkHere};

        MenuHighlighter.sortByConfidence(entries, confidenceByName);

        assertSame(unflagged, entries[0]);
        assertSame(walkHere, entries[1]);
        assertSame(moderate, entries[2]);
        assertSame(high, entries[3]);
    }

    @Test
    public void preservesRelativeOrderWithinSameConfidence()
    {
        Map<String, DetectionConfidence> confidenceByName = Collections.singletonMap("auto bot", DetectionConfidence.HIGH);
        MenuEntry firstHigh = testMenuEntry("Trade with", "<col=ffffff>Auto Bot<col=ff0000> (level-3)");
        MenuEntry secondHigh = testMenuEntry("Report", "<col=ffffff>Auto Bot<col=ff0000> (level-3)");
        MenuEntry[] entries = {firstHigh, secondHigh};

        MenuHighlighter.sortByConfidence(entries, confidenceByName);

        assertSame(firstHigh, entries[0]);
        assertSame(secondHigh, entries[1]);
    }

    @Test
    public void extractsPlayerNameFromTaggedReportTarget()
    {
        assertEquals("Auto Bot", MenuHighlighter.extractPlayerNameFromTarget("<col=ffffff>Auto Bot<col=ffff00> (level-3)"));
    }

    @Test
    public void extractsPlayerNameFromMobileTaggedReportTarget()
    {
        assertEquals(
            "Auto Bot",
            MenuHighlighter.extractPlayerNameFromTarget("<col=ffffff>Auto Bot<col=ff0000> (level-3)<img=23>")
        );
    }

    @Test
    public void extractsPlayerNameWithoutLevelSuffix()
    {
        assertEquals("Auto Bot", MenuHighlighter.extractPlayerNameFromTarget("<img=1>Auto\u00A0Bot"));
    }

    @Test
    public void detectsMobileClientIconAfterCombatLevel()
    {
        assertTrue(MenuHighlighter.hasMobileClientIcon("<col=ffffff>Auto Bot<col=ff0000> (level-3)<img=23>"));
        assertTrue(MenuHighlighter.hasMobileClientIcon("<col=ffffff>Auto Bot<col=ff0000> (level-3) (Score: 120)<img=23>"));
        assertTrue(MenuHighlighter.hasMobileClientIcon("<col=ff4040>Auto Bot (level-3) (Score: 120)</col><img=23>"));
        assertFalse(MenuHighlighter.hasMobileClientIcon("<img=23><col=ffffff>Auto Bot<col=ff0000> (level-3)"));
        assertFalse(MenuHighlighter.hasMobileClientIcon("<col=ffffff>Auto Bot<col=ff0000> (level-3)"));
    }

    @Test
    public void appendsDetectionScoreAfterCombatLevel()
    {
        MenuEntry entry = testMenuEntry("Report", "<col=ffffff>Username<col=ff0000> (level-98)");
        Map<String, Integer> scoresByName = Collections.singletonMap("username", 120);

        MenuHighlighter.appendScores(new MenuEntry[]{entry}, scoresByName, 80, 110);

        assertEquals(
            "<col=ffffff>Username<col=ff0000> (level-98) "
                + MenuHighlighter.colorText("(Score: 120)", MenuHighlighter.HIGH_CONFIDENCE_HIGHLIGHT_COLOR),
            entry.getTarget()
        );
    }

    @Test
    public void appendsZeroForUnknownPlayerScores()
    {
        MenuEntry entry = testMenuEntry("Report", "<col=ffffff>Unknown Player<col=ff0000> (level-98)");

        MenuHighlighter.appendScores(new MenuEntry[]{entry}, Collections.emptyMap(), 80, 110);

        assertEquals(
            "<col=ffffff>Unknown Player<col=ff0000> (level-98) " + MenuHighlighter.colorText("(Score: 0)", Color.WHITE),
            entry.getTarget()
        );
    }

    @Test
    public void appendsDetectionScoreBeforeTrailingMobileIcon()
    {
        MenuEntry entry = testMenuEntry("Report", "<col=ffffff>Username<col=ff0000> (level-98)<img=23>");
        Map<String, Integer> scoresByName = Collections.singletonMap("username", 120);

        MenuHighlighter.appendScores(new MenuEntry[]{entry}, scoresByName, 80, 110);

        assertEquals(
            "<col=ffffff>Username<col=ff0000> (level-98) "
                + MenuHighlighter.colorText("(Score: 120)", MenuHighlighter.HIGH_CONFIDENCE_HIGHLIGHT_COLOR)
                + "<img=23>",
            entry.getTarget()
        );
    }

    @Test
    public void replacesExistingDetectionScoreWithoutDuplicating()
    {
        MenuEntry entry = testMenuEntry("Report", "<col=ffffff>Username<col=ff0000> (level-98)");

        MenuHighlighter.appendScores(new MenuEntry[]{entry}, Collections.singletonMap("username", 120), 80, 110);
        MenuHighlighter.appendScores(new MenuEntry[]{entry}, Collections.singletonMap("username", 140), 80, 110);

        assertEquals(
            "<col=ffffff>Username<col=ff0000> (level-98) "
                + MenuHighlighter.colorText("(Score: 140)", MenuHighlighter.HIGH_CONFIDENCE_HIGHLIGHT_COLOR),
            entry.getTarget()
        );
    }

    @Test
    public void appendsModerateDetectionScoreWithModerateColor()
    {
        MenuEntry entry = testMenuEntry("Report", "<col=ffffff>Username<col=ff0000> (level-98)");

        MenuHighlighter.appendScores(new MenuEntry[]{entry}, Collections.singletonMap("username", 80), 80, 110);

        assertEquals(
            "<col=ffffff>Username<col=ff0000> (level-98) "
                + MenuHighlighter.colorText("(Score: 80)", MenuHighlighter.MODERATE_CONFIDENCE_HIGHLIGHT_COLOR),
            entry.getTarget()
        );
    }

    @Test
    public void preservesScoreColorWhenHighlightingTarget()
    {
        MenuEntry entry = testMenuEntry("Report", "<col=ffffff>Username<col=ff0000> (level-98)");

        MenuHighlighter.appendScores(new MenuEntry[]{entry}, Collections.singletonMap("username", 0), 80, 110);
        MenuHighlighter.highlight(
            new MenuEntry[]{entry},
            Collections.singletonMap("username", DetectionConfidence.HIGH)
        );

        assertEquals(
            MenuHighlighter.colorText("Username (level-98)", MenuHighlighter.HIGH_CONFIDENCE_HIGHLIGHT_COLOR)
                + " "
                + MenuHighlighter.colorText("(Score: 0)", Color.WHITE),
            entry.getTarget()
        );
    }

    @Test
    public void extractsPlayerNameFromTargetWithDetectionScore()
    {
        assertEquals(
            "Auto Bot",
            MenuHighlighter.extractPlayerNameFromTarget("<col=ffffff>Auto Bot<col=ff0000> (level-3) (Score: 120)<img=23>")
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
                    case "toString":
                        return values[0] + " " + values[1];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        throw new UnsupportedOperationException(method.getName());
                }
            }
        );
    }
}
