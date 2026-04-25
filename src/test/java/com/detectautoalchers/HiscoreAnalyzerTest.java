package com.detectautoalchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.hiscore.Skill;
import org.junit.Test;

public class HiscoreAnalyzerTest
{
    @Test
    public void identifiesMagicDominantProfile()
    {
        HiscoreProfile profile = HiscoreAnalyzer.analyze(result(55, 2), 21, 10, 2);

        assertTrue(profile.isMagicDominant());
        assertEquals(55, profile.getMagicLevel());
        assertEquals(2, profile.getNonMagicSkillsAboveThreshold());
    }

    @Test
    public void rejectsProfilesWithTooManyOtherSkills()
    {
        HiscoreProfile profile = HiscoreAnalyzer.analyze(result(55, 3), 21, 10, 2);

        assertFalse(profile.isMagicDominant());
        assertEquals(3, profile.getNonMagicSkillsAboveThreshold());
    }

    @Test
    public void rejectsLowMagicProfiles()
    {
        HiscoreProfile profile = HiscoreAnalyzer.analyze(result(20, 0), 21, 10, 2);

        assertFalse(profile.isMagicDominant());
    }

    @Test
    public void nullResultIsNotFound()
    {
        HiscoreProfile profile = HiscoreAnalyzer.analyze(null, 21, 10, 2);

        assertEquals(HiscoreProfile.Status.NOT_FOUND, profile.getStatus());
        assertFalse(profile.isMagicDominant());
    }

    private HiscoreResult result(int magicLevel, int highNonMagicSkills)
    {
        Map<HiscoreSkill, Skill> skills = new EnumMap<>(HiscoreSkill.class);
        int raised = 0;
        for (HiscoreSkill hiscoreSkill : HiscoreSkill.values())
        {
            if (hiscoreSkill.getType() != HiscoreSkillType.SKILL)
            {
                continue;
            }

            int level = 1;
            if (hiscoreSkill == HiscoreSkill.MAGIC)
            {
                level = magicLevel;
            }
            else if (raised < highNonMagicSkills)
            {
                level = 11;
                raised++;
            }

            skills.put(hiscoreSkill, new Skill(1, level, 0));
        }

        return new HiscoreResult("player", skills);
    }
}
