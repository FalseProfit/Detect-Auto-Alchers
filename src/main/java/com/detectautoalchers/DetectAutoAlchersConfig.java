package com.detectautoalchers;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("detectautoalchers")
public interface DetectAutoAlchersConfig extends Config
{
    @ConfigItem(
        keyName = "radius",
        name = "Detection radius",
        description = "Maximum tile distance from you to monitor",
        position = 0
    )
    @Range(min = 1, max = 104)
    default int radius()
    {
        return 15;
    }

    @ConfigItem(
        keyName = "observationWindowSeconds",
        name = "Observation window",
        description = "Seconds of alchemy observations to keep for each player",
        position = 1
    )
    @Range(min = 10, max = 600)
    default int observationWindowSeconds()
    {
        return 60;
    }

    @ConfigItem(
        keyName = "castThreshold",
        name = "Cast threshold",
        description = "Alchemy-like observations required before behavior evidence scores",
        position = 2
    )
    @Range(min = 1, max = 100)
    default int castThreshold()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "suspicionThreshold",
        name = "Suspicion threshold",
        description = "Evidence score required before a player is highlighted",
        position = 3
    )
    @Range(min = 1, max = 150)
    default int suspicionThreshold()
    {
        return 80;
    }

    @ConfigItem(
        keyName = "requireFireStaff",
        name = "Require fire staff",
        description = "Require fire staff evidence before a player can be highlighted",
        position = 4
    )
    default boolean requireFireStaff()
    {
        return true;
    }

    @ConfigItem(
        keyName = "includeFireRuneStaves",
        name = "Broad fire staff match",
        description = "Include battlestaves and combination staves that provide fire runes",
        position = 5
    )
    default boolean includeFireRuneStaves()
    {
        return false;
    }

    @ConfigItem(
        keyName = "enableHiscoreScoring",
        name = "Hiscore scoring",
        description = "Look up likely candidates and add evidence for Magic-dominant accounts",
        position = 6
    )
    default boolean enableHiscoreScoring()
    {
        return true;
    }

    @ConfigItem(
        keyName = "magicLevelThreshold",
        name = "Magic threshold",
        description = "Minimum Magic level for the Magic-dominant hiscore rule",
        position = 7
    )
    @Range(min = 1, max = 99)
    default int magicLevelThreshold()
    {
        return 21;
    }

    @ConfigItem(
        keyName = "nonMagicSkillThreshold",
        name = "Other skill threshold",
        description = "Non-Magic skills above this level count against the Magic-dominant rule",
        position = 8
    )
    @Range(min = 1, max = 99)
    default int nonMagicSkillThreshold()
    {
        return 10;
    }

    @ConfigItem(
        keyName = "allowedNonMagicSkillsAboveThreshold",
        name = "Allowed other skills",
        description = "Maximum non-Magic skills above the other-skill threshold",
        position = 9
    )
    @Range(min = 0, max = 24)
    default int allowedNonMagicSkillsAboveThreshold()
    {
        return 2;
    }

    @ConfigItem(
        keyName = "enableMaxMagicScoring",
        name = "99 Magic scoring",
        description = "Add evidence for accounts still alching at or above the configured Magic level",
        position = 10
    )
    default boolean enableMaxMagicScoring()
    {
        return true;
    }

    @ConfigItem(
        keyName = "maxMagicLevelThreshold",
        name = "High Magic threshold",
        description = "Magic level at or above which high-Magic scoring applies",
        position = 11
    )
    @Range(min = 1, max = 99)
    default int maxMagicLevelThreshold()
    {
        return 99;
    }

    @ConfigItem(
        keyName = "maxMagicScore",
        name = "High Magic score",
        description = "Evidence score added for accounts at or above the high-Magic threshold",
        position = 12
    )
    @Range(min = 0, max = 200)
    default int maxMagicScore()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "enableMatureAccountSuppression",
        name = "Mature account suppression",
        description = "Reduce score for accounts with many non-Magic levels",
        position = 13
    )
    default boolean enableMatureAccountSuppression()
    {
        return true;
    }

    @ConfigItem(
        keyName = "nonMagicTotalLevelSuppressionThreshold",
        name = "Non-Magic total threshold",
        description = "Non-Magic total level at or above this value receives the mature-account score penalty",
        position = 14
    )
    @Range(min = 1, max = 2268)
    default int nonMagicTotalLevelSuppressionThreshold()
    {
        return 125;
    }

    @ConfigItem(
        keyName = "matureAccountScorePenalty",
        name = "Mature account penalty",
        description = "Score penalty applied to mature accounts",
        position = 15
    )
    @Range(min = 0, max = 500)
    default int matureAccountScorePenalty()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "hiscoreCooldownMinutes",
        name = "Hiscore cooldown",
        description = "Minutes before retrying a failed or missing hiscore lookup",
        position = 16
    )
    @Range(min = 1, max = 120)
    default int hiscoreCooldownMinutes()
    {
        return 15;
    }

    @ConfigItem(
        keyName = "alchemyAnimationIds",
        name = "Alchemy animation IDs",
        description = "Comma-separated animation IDs treated as alchemy-like",
        position = 17
    )
    default String alchemyAnimationIds()
    {
        return "713";
    }

    @ConfigItem(
        keyName = "alchemySpotAnimationIds",
        name = "Alchemy spotanim IDs",
        description = "Comma-separated spot-animation IDs treated as alchemy-like",
        position = 18
    )
    default String alchemySpotAnimationIds()
    {
        return "112,113";
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay",
        description = "Draw red outlines around highlighted suspects",
        position = 19
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "colorMenuEntries",
        name = "Color menu entries",
        description = "Color suspect player menu entries red",
        position = 20
    )
    default boolean colorMenuEntries()
    {
        return true;
    }

    @ConfigItem(
        keyName = "persistReportedPlayers",
        name = "Persist reported players",
        description = "Save reported players locally so they are not suggested again after restart",
        position = 21
    )
    default boolean persistReportedPlayers()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightReportedPlayers",
        name = "Highlight reported players",
        description = "Draw a separate outline around players already in local report history",
        position = 22
    )
    default boolean highlightReportedPlayers()
    {
        return true;
    }

    @ConfigItem(
        keyName = "reportedPlayerHighlightColor",
        name = "Reported highlight color",
        description = "Outline color for players already in local report history",
        position = 23
    )
    default Color reportedPlayerHighlightColor()
    {
        return new Color(144, 238, 144);
    }
}
