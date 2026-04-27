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
        description = "Alchemy-like observations required inside the observation window before repeated-casting behavior counts. Default: 5 observations.",
        position = 2,
        section = basicSection
    )
    @Range(min = 1, max = 100)
    default int castThreshold()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "suspicionThreshold",
        name = "Suspicion threshold",
        description = "Final detection score required before the plugin highlights a suspected player. Default: 80.",
        position = 3,
        section = basicSection
    )
    @Range(min = 1, max = 150)
    default int suspicionThreshold()
    {
        return 80;
    }

    @ConfigItem(
        keyName = "requireFireStaff",
        name = "Require fire staff",
        description = "Require the player to be seen wielding a fire staff before they can be highlighted. Default: on.",
        position = 4,
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
        position = 5,
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
        position = 6,
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
        position = 7,
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
        position = 8,
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
        position = 9,
        section = basicSection
    )
    default String alchemySpotAnimationIds()
    {
        return "112,113";
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay",
        description = "Draw red outlines around players whose final detection score meets the suspicion threshold. Default: on.",
        position = 10,
        section = basicSection
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "colorMenuEntries",
        name = "Color menu entries",
        description = "Color right-click menu entries red for players currently highlighted as suspects. Default: on.",
        position = 11,
        section = basicSection
    )
    default boolean colorMenuEntries()
    {
        return true;
    }

    @ConfigItem(
        keyName = "persistReportedPlayers",
        name = "Persist reported players",
        description = "Save reported players locally so they are not suggested again after restarting RuneLite. Default: on.",
        position = 12,
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
        position = 13,
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
        position = 14,
        section = basicSection
    )
    default Color reportedPlayerHighlightColor()
    {
        return new Color(144, 238, 144);
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
        description = "Minimum Magic level required before the Magic-dominant hiscore rule can add score. Default: 21.",
        position = 1,
        section = scoreIncreasesSection
    )
    @Range(min = 1, max = 99)
    default int magicLevelThreshold()
    {
        return 21;
    }

    @ConfigItem(
        keyName = "nonMagicSkillThreshold",
        name = "Other skill threshold",
        description = "Some smart alch bots increase a small number of non-Magic skills further than necessary to avoid detection i.e. training crafting and fletching to 60, plus training magic, but everything else untouched. This controls what level of non-Magic skills can be ignored when calculating the Magic-dominant account rule and subsequent detection score. How many skills ignored is configurable below. Default: 50.",
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
        description = "Some smart alch bots increase a small number of non-Magic skills further than necessary to avoid detection i.e. training crafting and fletching to 60, plus training magic, but everything else untouched. This controls the maximum non-Magic skills allowed above the other-skill threshold to not be counted towards the Magic-dominant hiscore rule. Skill level configured above. Default: 2 skills.",
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
        description = "Add extra score for accounts still alching at or above the configured high-Magic level. Why would real players alch past 99? Default: on.",
        position = 4,
        section = scoreIncreasesSection
    )
    default boolean enableMaxMagicScoring()
    {
        return true;
    }

    @ConfigItem(
        keyName = "maxMagicLevelThreshold",
        name = "High Magic threshold",
        description = "Magic level at or above which high-Magic scoring applies. This almost certainly does not need to be a configurable value. Likely TODO: remove configurable value and hardcode magic level 99. Default: 99.",
        position = 5,
        section = scoreIncreasesSection
    )
    @Range(min = 1, max = 99)
    default int maxMagicLevelThreshold()
    {
        return 99;
    }

    @ConfigItem(
        keyName = "maxMagicScore",
        name = "High Magic score",
        description = "Detection score added for accounts at or above the high-Magic threshold. Why would real players alch past 99? High likelihood of botting. Default: +100.",
        position = 6,
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
        description = "Non-Magic total level at or above this value receives the non-Magic total score reduction. Default: 125.",
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
        description = "Combined clue scroll completions and collection-log items above this value receive the clue/log score reduction. Default: 5.",
        position = 3,
        section = scoreReductionsSection
    )
    @Range(min = 0, max = 1000)
    default int clueCollectionActivityThreshold()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "clueCollectionActivityScorePenalty",
        name = "Clue/log penalty",
        description = "Detection score subtracted when combined clue scroll completions and collection-log items exceed the clue/log threshold. Default: -100.",
        position = 4,
        section = scoreReductionsSection
    )
    @Range(min = 0, max = 500)
    default int clueCollectionActivityScorePenalty()
    {
        return 100;
    }
}
