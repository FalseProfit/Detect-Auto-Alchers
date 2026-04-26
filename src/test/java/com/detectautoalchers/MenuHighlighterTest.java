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
}
