package com.detectautoalchers;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("detectautoalchers")
public interface DetectAutoAlchersConfig extends Config
{
    @ConfigSection(
        name = "Basic",
        description = "General detection, lookup, and highlighting options",
        position = 0
    )
    String basicSection = "basic";

    @ConfigSection(
        name = "Score Increases",
        description = "Signals that add to a player's detection score",
        position = 1,
        closedByDefault = true
    )
    String scoreIncreasesSection = "scoreIncreases";

    @ConfigSection(
        name = "Score Reductions",
        description = "Signals that subtract from a player's detection score",
        position = 2,
        closedByDefault = true
    )
    String scoreReductionsSection = "scoreReductions";

    @ConfigSection(
        name = "Debug",
        description = "Debugging options for inspecting detector state",
        position = 3,
        closedByDefault = true
    )
    String debugSection = "debug";

    @ConfigItem(
        keyName = "radius",
        name = "Detection radius",
        description = "Maximum tile distance from your player to monitor nearby players for alchemy behavior. Default: 15 tiles.",
        position = 0,
        section = basicSection
    )
    @Range(min = 1, max = 104)
    default int radius()
    {
        return 15;
    }

    @ConfigItem(
        keyName = "observationWindowSeconds",
        name = "Observation window",
        description = "Seconds of recent alchemy-like animations or spot animations to keep for each nearby player. Default: 60 seconds.",
        position = 1,
        section = basicSection
    )
    @Range(min = 10, max = 600)
    default int observationWindowSeconds()
    {
        return 60;
    }

    @ConfigItem(
        keyName = "castThreshold",
        name = "Cast threshold",
        description = "Alchemy-like observations required inside the observation window before repeated-casting behavior counts. Set to 0 to allow fire-staff players to receive hiscore lookups and score-only detection without recent casts. Default: 5 observations.",
        position = 2,
        section = basicSection
    )
    @Range(min = 0, max = 100)
    default int castThreshold()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "suspicionThreshold",
        name = "Moderate confidence threshold",
        description = "Final detection score required before the plugin highlights a moderate-confidence suspected player. Default: 80.",
        position = 3,
        section = basicSection
    )
    @Range(min = 1, max = 150)
    default int suspicionThreshold()
    {
        return 80;
    }

    @ConfigItem(
        keyName = "highConfidenceMargin",
        name = "High confidence margin",
        description = "Additional score above the moderate confidence threshold required before the plugin highlights a high-confidence suspected player. Default: +30.",
        position = 4,
        section = basicSection
    )
    @Range(min = 1, max = 300)
    default int highConfidenceMargin()
    {
        return 30;
    }

    @ConfigItem(
        keyName = "highConfidenceThreshold",
        name = "High confidence threshold",
        description = "Legacy absolute high-confidence threshold.",
        hidden = true
    )
    @Range(min = 1, max = 300)
    default int highConfidenceThreshold()
    {
        return 110;
    }

    @ConfigItem(
        keyName = "requireFireStaff",
        name = "Require fire staff",
        description = "Require the player to be seen wielding a fire staff before they can be highlighted. Default: on.",
        position = 5,
        section = basicSection
    )
    default boolean requireFireStaff()
    {
        return true;
    }

    @ConfigItem(
        keyName = "includeFireRuneStaves",
        name = "Broad fire staff match",
        description = "Also treat battlestaves and combination staves that provide fire runes as staff evidence. Default: off.",
        position = 6,
        section = basicSection
    )
    default boolean includeFireRuneStaves()
    {
        return false;
    }

    @ConfigItem(
        keyName = "ignoreMobilePlayers",
        name = "Ignore mobile players",
        description = "Ignore players when the right-click menu shows the mobile client icon next to their name. Default: on.",
        position = 7,
        section = basicSection
    )
    default boolean ignoreMobilePlayers()
    {
        return true;
    }

    @ConfigItem(
        keyName = "hiscoreCooldownMinutes",
        name = "Hiscore cooldown",
        description = "Minutes to wait before retrying a failed or missing hiscore lookup for the same player. Default: 3 minutes.",
        position = 8,
        section = basicSection
    )
    @Range(min = 1, max = 120)
    default int hiscoreCooldownMinutes()
    {
        return 3;
    }

    @ConfigItem(
        keyName = "alchemyAnimationIds",
        name = "Alchemy animation IDs",
        description = "Comma-separated player animation IDs treated as alchemy-like observations. Default: 713.",
        position = 9,
        section = basicSection
    )
    default String alchemyAnimationIds()
    {
        return "713";
    }

    @ConfigItem(
        keyName = "alchemySpotAnimationIds",
        name = "Alchemy spotanim IDs",
        description = "Comma-separated spot-animation IDs treated as alchemy-like observations. Default: 112,113.",
        position = 10,
        section = basicSection
    )
    default String alchemySpotAnimationIds()
    {
        return "112,113";
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay",
        description = "Draw colored outlines around players whose final detection score meets a confidence threshold. Default: on.",
        position = 11,
        section = basicSection
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "colorMenuEntries",
        name = "Color menu entries",
        description = "Color right-click menu entries for players currently highlighted as suspects. Default: on.",
        position = 12,
        section = basicSection
    )
    default boolean colorMenuEntries()
    {
        return true;
    }

    @ConfigItem(
        keyName = "sortMenuEntriesByConfidence",
        name = "Sort menu entries",
        description = "Sort right-click player menu entries by detection confidence: high first, then moderate, then unflagged. Default: on.",
        position = 13,
        section = basicSection
    )
    default boolean sortMenuEntriesByConfidence()
    {
        return true;
    }

    @ConfigItem(
        keyName = "persistReportedPlayers",
        name = "Persist reported players",
        description = "Save reported players locally so they are not suggested again after restarting RuneLite. Default: on.",
        position = 14,
        section = basicSection
    )
    default boolean persistReportedPlayers()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightReportedPlayers",
        name = "Highlight reported players",
        description = "Draw a separate outline around players already saved in local report history. Default: on.",
        position = 15,
        section = basicSection
    )
    default boolean highlightReportedPlayers()
    {
        return true;
    }

    @ConfigItem(
        keyName = "reportedPlayerHighlightColor",
        name = "Reported highlight color",
        description = "Outline color for players already saved in local report history. Default: RGB 144,238,144.",
        position = 16,
        section = basicSection
    )
    default Color reportedPlayerHighlightColor()
    {
        return new Color(144, 238, 144);
    }

    @ConfigItem(
        keyName = "compactPanelMode",
        name = "Compact panel mode",
        description = "Show shorter suspect rows in the side panel. Default: off.",
        position = 17,
        section = basicSection
    )
    default boolean compactPanelMode()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showMenuDetectionScores",
        name = "Show menu detection scores",
        description = "Show each right-clicked player's current detection score for debugging. Unknown, untracked, and suppressed players display 0. Default: off.",
        position = 0,
        section = debugSection
    )
    default boolean showMenuDetectionScores()
    {
        return false;
    }

    @ConfigItem(
        keyName = "enableHiscoreScoring",
        name = "Hiscore scoring",
        description = "Look up likely candidates and add score for accounts whose hiscores look Magic-dominant. Default: on.",
        position = 0,
        section = scoreIncreasesSection
    )
    default boolean enableHiscoreScoring()
    {
        return true;
    }

    @ConfigItem(
        keyName = "magicLevelThreshold",
        name = "Magic threshold",
        description = "Minimum Magic level required before a target player can receive detection score. Default: 53. Set to 21 to include Low Alchemy bots.",
        position = 1,
        section = scoreIncreasesSection
    )
    @Range(min = 1, max = 99)
    default int magicLevelThreshold()
    {
        return 53;
    }

    @ConfigItem(
        keyName = "nonMagicSkillThreshold",
        name = "Other skill threshold",
        description = "Some smart alch bots increase a small number of non-Magic skills further than necessary to avoid detection<br>i.e. training crafting and fletching to 60, plus training magic, but everything else untouched.<br>This controls what level of non-Magic skills can be ignored when calculating the Magic-dominant account rule and subsequent detection score.<br>How many skills ignored is configurable below. Default: 50.",
        position = 2,
        section = scoreIncreasesSection
    )
    @Range(min = 1, max = 99)
    default int nonMagicSkillThreshold()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "allowedNonMagicSkillsAboveThreshold",
        name = "Allowed other skills",
        description = "Some smart alch bots increase a small number of non-Magic skills further than necessary to avoid detection<br>i.e. training crafting and fletching to 60, plus training magic, but everything else untouched.<br>This controls the maximum non-Magic skills allowed above the other-skill threshold to not be counted towards the Magic-dominant hiscore rule.<br>Skill level configured above. Default: 2 skills.",
        position = 3,
        section = scoreIncreasesSection
    )
    @Range(min = 0, max = 24)
    default int allowedNonMagicSkillsAboveThreshold()
    {
        return 2;
    }

    @ConfigItem(
        keyName = "enableMaxMagicScoring",
        name = "99 Magic scoring",
        description = "Add extra score for accounts still alching at 99 Magic. Why would real players alch past 99? Default: on.",
        position = 4,
        section = scoreIncreasesSection
    )
    default boolean enableMaxMagicScoring()
    {
        return true;
    }

    @ConfigItem(
        keyName = "maxMagicScore",
        name = "High Magic score",
        description = "Detection score added for accounts at 99 Magic. Why would real players alch past 99? High likelihood of botting. Default: +100.",
        position = 5,
        section = scoreIncreasesSection
    )
    @Range(min = 0, max = 200)
    default int maxMagicScore()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "enableMatureAccountSuppression",
        name = "Non-Magic total reduction",
        description = "Subtract bot score from accounts with enough non-Magic total level to look less like a fresh alching account. Default: on.",
        position = 0,
        section = scoreReductionsSection
    )
    default boolean enableMatureAccountSuppression()
    {
        return true;
    }

    @ConfigItem(
        keyName = "nonMagicTotalLevelSuppressionThreshold",
        name = "Non-Magic total threshold",
        description = "Non-Magic total level at or above this value receives the non-Magic total score reduction. Default: 150.",
        position = 1,
        section = scoreReductionsSection
    )
    @Range(min = 1, max = 2268)
    default int nonMagicTotalLevelSuppressionThreshold()
    {
        return 150;
    }

    @ConfigItem(
        keyName = "matureAccountScorePenalty",
        name = "Non-Magic total penalty",
        description = "Detection score subtracted when the non-Magic total threshold is met. Default: -100.",
        position = 2,
        section = scoreReductionsSection
    )
    @Range(min = 0, max = 500)
    default int matureAccountScorePenalty()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "clueCollectionActivityThreshold",
        name = "Clue/log threshold",
        description = "Combined clue scroll completions and collection-log items at or above this value receive the clue/log score reduction. Default: 4.",
        position = 3,
        section = scoreReductionsSection
    )
    @Range(min = 1, max = 1000)
    default int clueCollectionActivityThreshold()
    {
        return 4;
    }

    @ConfigItem(
        keyName = "clueCollectionActivityScorePenalty",
        name = "Clue/log penalty",
        description = "Detection score subtracted when combined clue scroll completions and collection-log items meet the clue/log threshold. Default: -100.",
        position = 4,
        section = scoreReductionsSection
    )
    @Range(min = 0, max = 500)
    default int clueCollectionActivityScorePenalty()
    {
        return 100;
    }
}
