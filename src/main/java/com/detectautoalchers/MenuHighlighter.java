package com.detectautoalchers;

import java.awt.Color;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

final class MenuHighlighter
{
    static final Color HIGHLIGHT_COLOR = new Color(255, 64, 64);
    private static final Pattern LEVEL_SUFFIX_WITH_TRAILING_ICON = Pattern.compile(
        "(?i).*\\(level-\\d+\\)\\s*(?:<img=\\d+>\\s*)+$"
    );
    private static final Pattern TRAILING_IMAGE_TAGS = Pattern.compile("(?i)(?:\\s*<img=\\d+>)+\\s*$");

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

            entry.setTarget(colorTarget(entry.getTarget()));
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

    static String extractPlayerNameFromTarget(String target)
    {
        String cleaned = cleanText(target)
            .replace('\u00A0', ' ')
            .replace("&nbsp;", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return cleaned.replaceFirst("\\s+\\(level-\\d+\\)$", "").trim();
    }

    static boolean hasMobileClientIcon(String target)
    {
        return target != null && LEVEL_SUFFIX_WITH_TRAILING_ICON.matcher(target).matches();
    }

    static String colorTarget(String target)
    {
        String trailingImages = "";
        java.util.regex.Matcher matcher = TRAILING_IMAGE_TAGS.matcher(target == null ? "" : target);
        if (matcher.find())
        {
            trailingImages = matcher.group().trim();
        }

        return colorText(cleanText(target)) + trailingImages;
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
