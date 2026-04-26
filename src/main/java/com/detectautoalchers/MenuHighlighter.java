package com.detectautoalchers;

import java.awt.Color;
import java.util.Set;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

final class MenuHighlighter
{
    static final Color HIGHLIGHT_COLOR = new Color(255, 64, 64);

    private MenuHighlighter()
    {
    }

    static void highlight(MenuEntry[] menuEntries, Set<String> suspiciousNames)
    {
        if (menuEntries == null || suspiciousNames.isEmpty())
        {
            return;
        }

        for (MenuEntry entry : menuEntries)
        {
            if (entry == null || !shouldHighlight(entry, suspiciousNames))
            {
                continue;
            }

            entry.setTarget(colorText(cleanText(entry.getTarget())));
            if (isReportOption(entry.getOption()))
            {
                entry.setOption(colorText(cleanText(entry.getOption())));
            }
        }
    }

    static boolean shouldHighlightTarget(String target, Set<String> suspiciousNames)
    {
        return !findMatchingSuspiciousName(target, suspiciousNames).isEmpty();
    }

    static String findMatchingSuspiciousName(String target, Set<String> suspiciousNames)
    {
        String normalizedTarget = DetectorService.normalizeName(Text.removeTags(target == null ? "" : target));
        for (String suspiciousName : suspiciousNames)
        {
            if (normalizedTarget.equals(suspiciousName)
                || normalizedTarget.startsWith(suspiciousName + " ")
                || normalizedTarget.startsWith(suspiciousName + " ("))
            {
                return suspiciousName;
            }
        }
        return "";
    }

    static String colorText(String text)
    {
        return ColorUtil.wrapWithColorTag(text, HIGHLIGHT_COLOR);
    }

    private static boolean shouldHighlight(MenuEntry entry, Set<String> suspiciousNames)
    {
        Player player = entry.getPlayer();
        if (player != null && suspiciousNames.contains(DetectorService.normalizeName(player.getName())))
        {
            return true;
        }

        return shouldHighlightTarget(entry.getTarget(), suspiciousNames);
    }

    private static boolean isReportOption(String option)
    {
        return "report".equalsIgnoreCase(Text.removeTags(option == null ? "" : option).trim());
    }

    static String cleanText(String target)
    {
        return Text.removeTags(target == null ? "" : target);
    }
}
