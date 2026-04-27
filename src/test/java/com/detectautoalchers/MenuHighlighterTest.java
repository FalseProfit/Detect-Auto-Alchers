package com.detectautoalchers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Set;
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
        assertFalse(MenuHighlighter.hasMobileClientIcon("<img=23><col=ffffff>Auto Bot<col=ff0000> (level-3)"));
        assertFalse(MenuHighlighter.hasMobileClientIcon("<col=ffffff>Auto Bot<col=ff0000> (level-3)"));
    }
}
