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
        assertTrue(profile.getNonMagicTotalLevel() < 125);
    }

    @Test
    public void recordsMatureNonMagicTotalLevel()
    {
        HiscoreProfile profile = HiscoreAnalyzer.analyze(resultWithNonMagicLevels(89, 50, 50, 10), 21, 50, 2);

        assertTrue(profile.isMagicDominant());
        assertTrue(profile.getNonMagicTotalLevel() >= 125);
        assertTrue(profile.isMatureAccount(125));
    }

    @Test
    public void keepsBelowThresholdProfilesUnsuppressed()
    {
        HiscoreProfile profile = HiscoreAnalyzer.analyze(resultWithNonMagicLevels(89, 20, 20), 21, 50, 2);

        assertTrue(profile.isMagicDominant());
        assertTrue(profile.getNonMagicTotalLevel() < 125);
        assertFalse(profile.isMatureAccount(125));
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
        int[] nonMagicLevels = new int[highNonMagicSkills];
        for (int i = 0; i < nonMagicLevels.length; i++)
        {
            nonMagicLevels[i] = 11;
        }

        return resultWithNonMagicLevels(magicLevel, nonMagicLevels);
    }

    private HiscoreResult resultWithNonMagicLevels(int magicLevel, int... raisedNonMagicLevels)
    {
        Map<HiscoreSkill, Skill> skills = new EnumMap<>(HiscoreSkill.class);
        int raisedIndex = 0;
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
            else if (raisedIndex < raisedNonMagicLevels.length)
            {
                level = raisedNonMagicLevels[raisedIndex];
                raisedIndex++;
            }

            skills.put(hiscoreSkill, new Skill(1, level, 0));
        }

        return new HiscoreResult("player", skills);
    }
}
