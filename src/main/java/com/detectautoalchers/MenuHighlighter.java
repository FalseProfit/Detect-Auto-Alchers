package com.detectautoalchers;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

final class MenuHighlighter
{
    static final Color HIGH_CONFIDENCE_HIGHLIGHT_COLOR = new Color(255, 64, 64);
    static final Color MODERATE_CONFIDENCE_HIGHLIGHT_COLOR = new Color(255, 220, 64);
    private static final Pattern LEVEL_SUFFIX_WITH_TRAILING_ICON = Pattern.compile(
        "(?i).*\\(level-\\d+\\)\\s*(?:<img=\\d+>\\s*)+$"
    );
    private static final Pattern TRAILING_IMAGE_TAGS = Pattern.compile("(?i)(?:\\s*<img=\\d+>)+\\s*$");

    private MenuHighlighter()
    {
    }

    static void highlight(MenuEntry[] menuEntries, Set<String> suspiciousNames)
    {
        Map<String, DetectionConfidence> confidenceByName = new LinkedHashMap<>();
        for (String suspiciousName : suspiciousNames)
        {
            confidenceByName.put(suspiciousName, DetectionConfidence.HIGH);
        }
        highlight(menuEntries, confidenceByName);
    }

    static void highlight(MenuEntry[] menuEntries, Map<String, DetectionConfidence> confidenceByName)
    {
        highlight(menuEntries, confidenceByName, Collections.emptySet(), null);
    }

    static void highlight(
        MenuEntry[] menuEntries,
        Map<String, DetectionConfidence> confidenceByName,
        Set<String> reportedNames,
        Color reportedColor)
    {
        if (menuEntries == null || (confidenceByName.isEmpty() && reportedNames.isEmpty()))
        {
            return;
        }

        for (MenuEntry entry : menuEntries)
        {
            if (entry == null)
            {
                continue;
            }

            Color color = reportedColorFor(entry, reportedNames, reportedColor);
            if (color == null)
            {
                DetectionConfidence confidence = confidenceFor(entry, confidenceByName);
                color = colorFor(confidence);
            }
            if (color == null)
            {
                continue;
            }

            entry.setTarget(colorTarget(entry.getTarget(), color));
            if (isReportOption(entry.getOption()))
            {
                entry.setOption(colorText(cleanText(entry.getOption()), color));
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
        return colorTarget(target, HIGH_CONFIDENCE_HIGHLIGHT_COLOR);
    }

    static String colorTarget(String target, Color color)
    {
        String trailingImages = "";
        java.util.regex.Matcher matcher = TRAILING_IMAGE_TAGS.matcher(target == null ? "" : target);
        if (matcher.find())
        {
            trailingImages = matcher.group().trim();
        }

        return colorText(cleanText(target), color) + trailingImages;
    }

    static String colorText(String text)
    {
        return colorText(text, HIGH_CONFIDENCE_HIGHLIGHT_COLOR);
    }

    static String colorText(String text, Color color)
    {
        return ColorUtil.wrapWithColorTag(text, color);
    }

    private static DetectionConfidence confidenceFor(
        MenuEntry entry,
        Map<String, DetectionConfidence> confidenceByName)
    {
        Player player = entry.getPlayer();
        if (player != null)
        {
            DetectionConfidence confidence = confidenceByName.get(DetectorService.normalizeName(player.getName()));
            if (confidence != null)
            {
                return confidence;
            }
        }

        String matchingName = findMatchingSuspiciousName(entry.getTarget(), confidenceByName.keySet());
        return confidenceByName.getOrDefault(matchingName, DetectionConfidence.NONE);
    }

    private static Color reportedColorFor(MenuEntry entry, Set<String> reportedNames, Color reportedColor)
    {
        if (reportedColor == null || reportedNames.isEmpty())
        {
            return null;
        }

        Player player = entry.getPlayer();
        if (player != null && reportedNames.contains(DetectorService.normalizeName(player.getName())))
        {
            return reportedColor;
        }

        String matchingName = findMatchingSuspiciousName(entry.getTarget(), reportedNames);
        return matchingName.isEmpty() ? null : reportedColor;
    }

    private static Color colorFor(DetectionConfidence confidence)
    {
        if (confidence == DetectionConfidence.HIGH)
        {
            return HIGH_CONFIDENCE_HIGHLIGHT_COLOR;
        }
        if (confidence == DetectionConfidence.MODERATE)
        {
            return MODERATE_CONFIDENCE_HIGHLIGHT_COLOR;
        }
        return null;
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
