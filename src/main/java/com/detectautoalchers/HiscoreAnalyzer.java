package com.detectautoalchers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.hiscore.Skill;

final class HiscoreAnalyzer
{
    private HiscoreAnalyzer()
    {
    }

    static HiscoreProfile analyze(
        HiscoreResult result,
        int magicLevelThreshold,
        int nonMagicSkillThreshold,
        int allowedNonMagicSkillsAboveThreshold)
    {
        if (result == null)
        {
            return HiscoreProfile.notFound();
        }

        Skill magic = result.getSkill(HiscoreSkill.MAGIC);
        int magicLevel = level(magic);
        int nonMagicHighSkills = 0;
        int nonMagicTotalLevel = 0;
        List<Integer> nonMagicLevels = new ArrayList<>();

        for (Map.Entry<HiscoreSkill, Skill> entry : result.getSkills().entrySet())
        {
            HiscoreSkill hiscoreSkill = entry.getKey();
            if (hiscoreSkill == HiscoreSkill.MAGIC || hiscoreSkill.getType() != HiscoreSkillType.SKILL)
            {
                continue;
            }

            int level = level(entry.getValue());
            nonMagicLevels.add(level);
            nonMagicTotalLevel += level;
            if (level > nonMagicSkillThreshold)
            {
                nonMagicHighSkills++;
            }
        }

        boolean magicDominant = magicLevel >= magicLevelThreshold
            && nonMagicHighSkills <= allowedNonMagicSkillsAboveThreshold;
        return HiscoreProfile.found(
            magicLevel,
            nonMagicHighSkills,
            nonMagicTotalLevel,
            activityCount(result.getSkill(HiscoreSkill.CLUE_SCROLL_ALL)),
            activityCount(result.getSkill(HiscoreSkill.COLLECTIONS_LOGGED)),
            magicDominant,
            toArray(nonMagicLevels)
        );
    }

    private static int[] toArray(List<Integer> levels)
    {
        int[] values = new int[levels.size()];
        for (int i = 0; i < levels.size(); i++)
        {
            values[i] = levels.get(i);
        }
        return values;
    }

    private static int level(Skill skill)
    {
        return skill == null ? 1 : skill.getLevel();
    }

    private static int activityCount(Skill skill)
    {
        if (skill == null || skill.getLevel() < 0)
        {
            return 0;
        }

        return skill.getLevel();
    }
}
