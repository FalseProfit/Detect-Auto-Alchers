package com.detectautoalchers;

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
        keyName = "hiscoreCooldownMinutes",
        name = "Hiscore cooldown",
        description = "Minutes before retrying a failed or missing hiscore lookup",
        position = 10
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
        position = 11
    )
    default String alchemyAnimationIds()
    {
        return "713";
    }

    @ConfigItem(
        keyName = "alchemySpotAnimationIds",
        name = "Alchemy spotanim IDs",
        description = "Comma-separated spot-animation IDs treated as alchemy-like",
        position = 12
    )
    default String alchemySpotAnimationIds()
    {
        return "112,113";
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay",
        description = "Draw red outlines around highlighted suspects",
        position = 13
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "colorMenuEntries",
        name = "Color menu entries",
        description = "Color suspect player menu entries red",
        position = 14
    )
    default boolean colorMenuEntries()
    {
        return true;
    }
}
