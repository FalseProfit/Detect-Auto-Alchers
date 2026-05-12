package com.detectautoalchers;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
    private static final Color LOW_SCORE_COLOR = Color.WHITE;
    private static final String SCORE_LABEL = "Alch Bot Score";
    private static final String SCORE_LABEL_PATTERN = "(?:alch\\s+bot\\s+score|score)";
    private static final Pattern LEVEL_SUFFIX_WITH_TRAILING_ICON = Pattern.compile(
        "(?i).*\\(level-\\d+\\)\\s*(?:\\(" + SCORE_LABEL_PATTERN + ":\\s*\\d+\\)\\s*)?(?:<img=\\d+>\\s*)+$"
    );
    private static final Pattern LEVEL_SUFFIX = Pattern.compile(
        "(?i).*\\(level-\\d+\\)(?:\\s+\\(" + SCORE_LABEL_PATTERN + ":\\s*\\d+\\))?\\s*$"
    );
    private static final Pattern SCORE_SUFFIX = Pattern.compile(
        "(?i)\\s*(?:<col=[0-9a-f]+>)?\\(" + SCORE_LABEL_PATTERN + ":\\s*\\d+\\)(?:</col>)?(\\s*(?:<img=\\d+>\\s*)*)$"
    );
    private static final Pattern SCORE_SUFFIX_TEXT = Pattern.compile(
        "(?i)\\s+\\(" + SCORE_LABEL_PATTERN + ":\\s*\\d+\\)\\s*$"
    );
    private static final Pattern COLOR_TAGS = Pattern.compile("(?i)</?col(?:=[0-9a-f]+)?>");
    private static final Pattern TRAILING_IMAGE_TAGS = Pattern.compile("(?i)(?:\\s*<img=\\d+>)+\\s*$");
    private static final Pattern TRAILING_SCORE_WITH_OPTIONAL_IMAGES = Pattern.compile(
        "(?i)(\\s*(?:<col=[0-9a-f]+>)?\\(" + SCORE_LABEL_PATTERN + ":\\s*\\d+\\)(?:</col>)?)(\\s*(?:<img=\\d+>\\s*)*)$"
    );

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

    static void sortByConfidence(MenuEntry[] menuEntries, Map<String, DetectionConfidence> confidenceByName)
    {
        if (menuEntries == null || confidenceByName == null || confidenceByName.isEmpty())
        {
            return;
        }

        Arrays.sort(menuEntries, Comparator.comparingInt(entry -> menuPriority(entry, confidenceByName)));
    }

    static void appendScores(
        MenuEntry[] menuEntries,
        Map<String, Integer> scoresByName,
        int moderateThreshold,
        int highThreshold)
    {
        if (menuEntries == null)
        {
            return;
        }

        Map<String, Integer> scores = scoresByName == null ? Collections.emptyMap() : scoresByName;
        for (MenuEntry entry : menuEntries)
        {
            if (entry == null || !isPlayerMenuEntry(entry))
            {
                continue;
            }
            if (!isReportOption(entry.getOption()))
            {
                continue;
            }

            String normalizedName = normalizedPlayerName(entry);
            int score = scores.getOrDefault(normalizedName, 0);
            entry.setTarget(appendScore(entry.getTarget(), score, moderateThreshold, highThreshold));
        }
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
        cleaned = SCORE_SUFFIX_TEXT.matcher(cleaned).replaceFirst("").trim();
        return cleaned.replaceFirst("(?i)\\s+\\(level-\\d+\\)$", "").trim();
    }

    static boolean hasMobileClientIcon(String target)
    {
        return target != null && LEVEL_SUFFIX_WITH_TRAILING_ICON.matcher(stripColorTags(target)).matches();
    }

    static String colorTarget(String target)
    {
        return colorTarget(target, HIGH_CONFIDENCE_HIGHLIGHT_COLOR);
    }

    static String colorTarget(String target, Color color)
    {
        ScoreSuffix scoreSuffix = splitScoreSuffix(target);
        String trailingImages = "";
        String targetWithoutScore = scoreSuffix.body;
        java.util.regex.Matcher matcher = TRAILING_IMAGE_TAGS.matcher(targetWithoutScore);
        if (matcher.find())
        {
            trailingImages = matcher.group().trim();
            targetWithoutScore = targetWithoutScore.substring(0, matcher.start()).replaceFirst("\\s+$", "");
        }

        return colorText(cleanText(targetWithoutScore), color) + scoreSuffix.scoreText + trailingImages + scoreSuffix.trailingImages;
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

    private static int menuPriority(MenuEntry entry, Map<String, DetectionConfidence> confidenceByName)
    {
        DetectionConfidence confidence = entry == null ? DetectionConfidence.NONE : confidenceFor(entry, confidenceByName);
        if (confidence == DetectionConfidence.HIGH)
        {
            return 2;
        }
        if (confidence == DetectionConfidence.MODERATE)
        {
            return 1;
        }
        return 0;
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

    static boolean isPlayerMenuEntry(MenuEntry entry)
    {
        if (entry.getPlayer() != null)
        {
            return true;
        }

        return LEVEL_SUFFIX.matcher(cleanText(entry.getTarget())).matches();
    }

    private static String normalizedPlayerName(MenuEntry entry)
    {
        Player player = entry.getPlayer();
        if (player != null && player.getName() != null)
        {
            return DetectorService.normalizeName(player.getName());
        }

        return DetectorService.normalizeName(extractPlayerNameFromTarget(entry.getTarget()));
    }

    private static String appendScore(String target, int score, int moderateThreshold, int highThreshold)
    {
        if (target == null || target.isEmpty())
        {
            return target;
        }

        String withoutExistingScore = SCORE_SUFFIX.matcher(target).replaceFirst("$1");
        java.util.regex.Matcher matcher = TRAILING_IMAGE_TAGS.matcher(withoutExistingScore);
        if (!matcher.find())
        {
            return withoutExistingScore + " " + formatScore(score, moderateThreshold, highThreshold);
        }

        String body = withoutExistingScore.substring(0, matcher.start()).replaceFirst("\\s+$", "");
        String trailingImages = matcher.group().trim();
        return body + " " + formatScore(score, moderateThreshold, highThreshold) + trailingImages;
    }

    private static String stripColorTags(String target)
    {
        return COLOR_TAGS.matcher(target).replaceAll("");
    }

    private static String formatScore(int score, int moderateThreshold, int highThreshold)
    {
        return colorText("(" + SCORE_LABEL + ": " + score + ")", scoreColor(score, moderateThreshold, highThreshold));
    }

    private static Color scoreColor(int score, int moderateThreshold, int highThreshold)
    {
        if (score >= highThreshold)
        {
            return HIGH_CONFIDENCE_HIGHLIGHT_COLOR;
        }
        if (score >= moderateThreshold)
        {
            return MODERATE_CONFIDENCE_HIGHLIGHT_COLOR;
        }
        return LOW_SCORE_COLOR;
    }

    private static ScoreSuffix splitScoreSuffix(String target)
    {
        String value = target == null ? "" : target;
        java.util.regex.Matcher matcher = TRAILING_SCORE_WITH_OPTIONAL_IMAGES.matcher(value);
        if (!matcher.find())
        {
            return new ScoreSuffix(value, "", "");
        }

        String body = value.substring(0, matcher.start()).replaceFirst("\\s+$", "");
        String scoreText = matcher.group(1);
        String trailingImages = matcher.group(2).trim();
        return new ScoreSuffix(body, scoreText, trailingImages);
    }

    private static final class ScoreSuffix
    {
        private final String body;
        private final String scoreText;
        private final String trailingImages;

        private ScoreSuffix(String body, String scoreText, String trailingImages)
        {
            this.body = body;
            this.scoreText = scoreText;
            this.trailingImages = trailingImages;
        }
    }
}
