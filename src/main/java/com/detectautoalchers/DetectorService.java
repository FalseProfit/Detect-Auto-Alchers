package com.detectautoalchers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class DetectorService
{
    private static final int MAX_HISCORE_LOOKUPS_IN_FLIGHT = 3;
    private static final long STALE_MULTIPLIER = 2L;
    private final Map<String, PlayerEvidence> evidenceByName = new LinkedHashMap<>();
    private final Map<String, EnumSet<SuppressionReason>> suppressionReasonsByName = new LinkedHashMap<>();

    synchronized void clear()
    {
        evidenceByName.clear();
        suppressionReasonsByName.clear();
    }

    synchronized void clearEvidence()
    {
        evidenceByName.clear();
    }

    synchronized void suppressName(String displayName)
    {
        suppressName(displayName, SuppressionReason.REPORTED);
    }

    synchronized void suppressName(String displayName, SuppressionReason reason)
    {
        String normalizedName = normalizeName(displayName);
        if (!normalizedName.isEmpty())
        {
            suppressionReasonsByName
                .computeIfAbsent(normalizedName, key -> EnumSet.noneOf(SuppressionReason.class))
                .add(reason);
            evidenceByName.remove(normalizedName);
        }
    }

    synchronized void suppressNames(Set<String> normalizedNames)
    {
        suppressNames(normalizedNames, SuppressionReason.REPORTED);
    }

    synchronized void suppressNames(Set<String> normalizedNames, SuppressionReason reason)
    {
        for (String normalizedName : normalizedNames)
        {
            if (!normalizedName.isEmpty())
            {
                suppressionReasonsByName
                    .computeIfAbsent(normalizedName, key -> EnumSet.noneOf(SuppressionReason.class))
                    .add(reason);
                evidenceByName.remove(normalizedName);
            }
        }
    }

    synchronized void unsuppressNames(Set<String> normalizedNames)
    {
        unsuppressNames(normalizedNames, SuppressionReason.REPORTED);
    }

    synchronized void unsuppressNames(Set<String> normalizedNames, SuppressionReason reason)
    {
        for (String normalizedName : normalizedNames)
        {
            EnumSet<SuppressionReason> reasons = suppressionReasonsByName.get(normalizedName);
            if (reasons == null)
            {
                continue;
            }

            reasons.remove(reason);
            if (reasons.isEmpty())
            {
                suppressionReasonsByName.remove(normalizedName);
            }
        }
    }

    synchronized void syncSuppressionReason(Set<String> normalizedNames, SuppressionReason reason)
    {
        Set<String> normalized = new HashSet<>();
        for (String normalizedName : normalizedNames)
        {
            String cleanName = normalizeName(normalizedName);
            if (!cleanName.isEmpty())
            {
                normalized.add(cleanName);
            }
        }

        List<String> previouslySuppressed = new ArrayList<>();
        for (Map.Entry<String, EnumSet<SuppressionReason>> entry : suppressionReasonsByName.entrySet())
        {
            if (entry.getValue().contains(reason))
            {
                previouslySuppressed.add(entry.getKey());
            }
        }

        for (String normalizedName : previouslySuppressed)
        {
            if (!normalized.contains(normalizedName))
            {
                unsuppressNames(Set.of(normalizedName), reason);
            }
        }
        suppressNames(normalized, reason);
    }

    synchronized boolean isSuppressed(String displayName)
    {
        return suppressionReasonsByName.containsKey(normalizeName(displayName));
    }

    synchronized String getDisplayName(String normalizedName)
    {
        PlayerEvidence evidence = evidenceByName.get(normalizeName(normalizedName));
        return evidence == null ? normalizedName : evidence.getDisplayName();
    }

    synchronized String updatePlayer(
        String displayName,
        int world,
        int distance,
        int weaponId,
        long nowMillis)
    {
        String normalizedName = normalizeName(displayName);
        if (normalizedName.isEmpty())
        {
            return "";
        }
        if (suppressionReasonsByName.containsKey(normalizedName))
        {
            evidenceByName.remove(normalizedName);
            return normalizedName;
        }

        PlayerEvidence evidence = evidenceByName.computeIfAbsent(
            normalizedName,
            key -> new PlayerEvidence(key, displayName)
        );
        evidence.updateSeen(displayName, world, distance, weaponId, nowMillis);
        return normalizedName;
    }

    synchronized boolean recordAlchObservation(
        String displayName,
        String source,
        int id,
        int tick,
        long nowMillis,
        DetectorConfigSnapshot config)
    {
        String normalizedName = normalizeName(displayName);
        if (normalizedName.isEmpty())
        {
            return false;
        }
        if (suppressionReasonsByName.containsKey(normalizedName))
        {
            evidenceByName.remove(normalizedName);
            return false;
        }

        PlayerEvidence evidence = evidenceByName.get(normalizedName);
        if (evidence == null)
        {
            evidence = new PlayerEvidence(normalizedName, displayName);
            evidenceByName.put(normalizedName, evidence);
        }

        return evidence.recordObservation(source, id, tick, nowMillis, config.getObservationWindowMillis());
    }

    synchronized void recompute(DetectorConfigSnapshot config, long nowMillis)
    {
        for (PlayerEvidence evidence : evidenceByName.values())
        {
            evidence.setLastResult(score(evidence, config, nowMillis));
        }
    }

    synchronized void pruneStale(long nowMillis, long observationWindowMillis)
    {
        long staleAfterMillis = Math.max(120_000L, observationWindowMillis * STALE_MULTIPLIER);
        Iterator<PlayerEvidence> iterator = evidenceByName.values().iterator();
        while (iterator.hasNext())
        {
            PlayerEvidence evidence = iterator.next();
            if (nowMillis - evidence.getLastSeenMillis() > staleAfterMillis)
            {
                iterator.remove();
            }
        }
    }

    synchronized boolean markHiscoreLookupIfNeeded(
        String normalizedName,
        DetectorConfigSnapshot config,
        long nowMillis)
    {
        if (!config.isEnableHiscoreScoring())
        {
            return false;
        }

        PlayerEvidence evidence = evidenceByName.get(normalizedName);
        if (evidence == null || suppressionReasonsByName.containsKey(normalizedName) || evidence.isHiscoreLookupInFlight())
        {
            return false;
        }

        HiscoreProfile profile = evidence.getHiscoreProfile();
        if (profile.isTerminalSuccess())
        {
            return false;
        }

        int castCount = evidence.getObservationCount(nowMillis, config.getObservationWindowMillis());
        boolean castGateDisabled = config.getCastThreshold() == 0;
        boolean behaviorMatch = !castGateDisabled && castCount >= config.getCastThreshold();
        boolean candidate = behaviorMatch
            || (staffMatches(evidence, config) && (castGateDisabled || castCount > 0));
        if (!candidate)
        {
            return false;
        }

        if (!hasHiscoreLookupCapacity())
        {
            return false;
        }

        if (evidence.getLastHiscoreLookupMillis() > 0
            && nowMillis - evidence.getLastHiscoreLookupMillis() < config.getHiscoreCooldownMillis())
        {
            return false;
        }

        evidence.setLastHiscoreLookupMillis(nowMillis);
        evidence.setHiscoreLookupInFlight(true);
        evidence.setHiscoreProfile(HiscoreProfile.pending());
        return true;
    }

    synchronized int getHiscoreLookupsInFlight()
    {
        int count = 0;
        for (PlayerEvidence evidence : evidenceByName.values())
        {
            if (evidence.isHiscoreLookupInFlight())
            {
                count++;
            }
        }
        return count;
    }

    synchronized boolean hasHiscoreLookupCapacity()
    {
        return getHiscoreLookupsInFlight() < MAX_HISCORE_LOOKUPS_IN_FLIGHT;
    }

    synchronized void applyHiscore(String normalizedName, HiscoreProfile hiscoreProfile)
    {
        PlayerEvidence evidence = evidenceByName.get(normalizedName);
        if (evidence == null)
        {
            return;
        }

        evidence.setHiscoreLookupInFlight(false);
        evidence.setHiscoreProfile(hiscoreProfile);
    }

    synchronized List<SuspicionResult> getSuspiciousResults()
    {
        List<SuspicionResult> results = new ArrayList<>();
        for (PlayerEvidence evidence : evidenceByName.values())
        {
            SuspicionResult result = evidence.getLastResult();
            if (result != null && result.isSuspicious() && !suppressionReasonsByName.containsKey(result.getNormalizedName()))
            {
                results.add(result);
            }
        }

        results.sort(Comparator
            .comparingInt(SuspicionResult::getScore).reversed()
            .thenComparing(SuspicionResult::getDisplayName));
        return results;
    }

    synchronized Set<String> getSuspiciousNames()
    {
        Set<String> names = new HashSet<>();
        for (SuspicionResult result : getSuspiciousResults())
        {
            names.add(result.getNormalizedName());
        }
        return names;
    }

    synchronized Map<String, DetectionConfidence> getSuspiciousConfidenceByName()
    {
        Map<String, DetectionConfidence> confidenceByName = new LinkedHashMap<>();
        for (SuspicionResult result : getSuspiciousResults())
        {
            confidenceByName.put(result.getNormalizedName(), result.getConfidence());
        }
        return confidenceByName;
    }

    synchronized Map<String, Integer> getScoresByName()
    {
        Map<String, Integer> scoresByName = new LinkedHashMap<>();
        for (PlayerEvidence evidence : evidenceByName.values())
        {
            SuspicionResult result = evidence.getLastResult();
            if (result != null && !suppressionReasonsByName.containsKey(result.getNormalizedName()))
            {
                scoresByName.put(result.getNormalizedName(), result.getScore());
            }
        }
        return scoresByName;
    }

    synchronized Map<String, SuspicionResult> getResultsByName(Set<String> normalizedNames)
    {
        Map<String, SuspicionResult> resultsByName = new LinkedHashMap<>();
        for (String normalizedName : normalizedNames)
        {
            PlayerEvidence evidence = evidenceByName.get(normalizeName(normalizedName));
            if (evidence != null && evidence.getLastResult() != null)
            {
                resultsByName.put(evidence.getNormalizedName(), evidence.getLastResult());
            }
        }
        return resultsByName;
    }

    synchronized boolean isSuspicious(String displayName)
    {
        PlayerEvidence evidence = evidenceByName.get(normalizeName(displayName));
        return evidence != null
            && !suppressionReasonsByName.containsKey(evidence.getNormalizedName())
            && evidence.getLastResult() != null
            && evidence.getLastResult().isSuspicious();
    }

    synchronized DetectionConfidence getConfidence(String displayName)
    {
        PlayerEvidence evidence = evidenceByName.get(normalizeName(displayName));
        if (evidence == null
            || suppressionReasonsByName.containsKey(evidence.getNormalizedName())
            || evidence.getLastResult() == null)
        {
            return DetectionConfidence.NONE;
        }

        return evidence.getLastResult().getConfidence();
    }

    synchronized String findSuspiciousNameFromTarget(String target)
    {
        Set<String> suspiciousNames = getSuspiciousNames();
        return MenuHighlighter.findMatchingSuspiciousName(target, suspiciousNames);
    }

    static String normalizeName(String name)
    {
        if (name == null)
        {
            return "";
        }

        return name
            .replace('\u00A0', ' ')
            .replace("&nbsp;", " ")
            .replaceAll("<[^>]*>", "")
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    private SuspicionResult score(PlayerEvidence evidence, DetectorConfigSnapshot config, long nowMillis)
    {
        evidence.prune(nowMillis, config.getObservationWindowMillis());
        int score = 0;
        int castCount = evidence.getObservationCount(nowMillis, config.getObservationWindowMillis());
        boolean staffMatch = staffMatches(evidence, config);
        int castThreshold = config.getCastThreshold();
        boolean castGateDisabled = castThreshold == 0;
        boolean behaviorMatch = castThreshold > 0 && castCount >= castThreshold;
        HiscoreProfile hiscoreProfile = evidence.getHiscoreProfile();
        boolean belowMagicThreshold = config.isEnableHiscoreScoring()
            && hiscoreProfile.isBelowMagicLevel(config.getMagicLevelThreshold());
        boolean magicDominant = config.isEnableHiscoreScoring()
            && !belowMagicThreshold
            && hiscoreProfile.isAtLeastMagicLevel(config.getMagicLevelThreshold())
            && hiscoreProfile.hasAtMostNonMagicSkillsAboveThreshold(
                config.getNonMagicSkillThreshold(),
                config.getAllowedNonMagicSkillsAboveThreshold()
            );
        boolean highMagic = config.isEnableHiscoreScoring()
            && !belowMagicThreshold
            && config.isEnableMaxMagicScoring()
            && hiscoreProfile.isAtLeastMagicLevel(DetectorConfigSnapshot.HIGH_MAGIC_LEVEL);
        boolean consistentCadence = behaviorMatch && evidence.hasConsistentCadence(
            nowMillis,
            config.getObservationWindowMillis(),
            castThreshold
        );

        int staffScore = 0;
        int behaviorScore = 0;
        int hiscoreScore = 0;
        int highMagicScore = 0;
        int cadenceScore = 0;
        int matureAccountPenalty = 0;
        int clueCollectionPenalty = 0;

        if (!belowMagicThreshold && staffMatch)
        {
            staffScore = DetectorConfigSnapshot.STAFF_SCORE;
            score += staffScore;
        }

        if (!belowMagicThreshold && behaviorMatch)
        {
            behaviorScore = DetectorConfigSnapshot.BEHAVIOR_SCORE;
            score += behaviorScore;
        }

        if (!belowMagicThreshold && magicDominant)
        {
            hiscoreScore = DetectorConfigSnapshot.HISCORE_SCORE;
            score += hiscoreScore;
        }

        if (!belowMagicThreshold && highMagic)
        {
            highMagicScore = config.getMaxMagicScore();
            score += highMagicScore;
        }

        if (!belowMagicThreshold && consistentCadence)
        {
            cadenceScore = DetectorConfigSnapshot.CADENCE_SCORE;
            score += cadenceScore;
        }

        boolean matureAccountSuppressed = config.isEnableMatureAccountSuppression()
            && hiscoreProfile.isMatureAccount(config.getNonMagicTotalLevelSuppressionThreshold());
        if (matureAccountSuppressed)
        {
            matureAccountPenalty = config.getMatureAccountScorePenalty();
            score = Math.max(0, score - matureAccountPenalty);
        }

        boolean clueCollectionActivitySuppressed = hiscoreProfile.hasClueOrCollectionLogActivity(
            config.getClueCollectionActivityThreshold()
        );
        if (clueCollectionActivitySuppressed)
        {
            clueCollectionPenalty = config.getClueCollectionActivityScorePenalty();
            score = Math.max(0, score - clueCollectionPenalty);
        }

        boolean castGatePassed = castGateDisabled || behaviorMatch;
        boolean staffGatePassed = !config.isRequireFireStaff() || staffMatch;
        boolean detectionGatePassed = castGatePassed && staffGatePassed;
        DetectionConfidence confidence = DetectionConfidence.fromScore(
            score,
            detectionGatePassed,
            config.getSuspicionThreshold(),
            config.getHighConfidenceThreshold()
        );

        return new SuspicionResult(
            evidence.getNormalizedName(),
            evidence.getDisplayName(),
            score,
            confidence,
            castCount,
            staffMatch,
            behaviorMatch,
            magicDominant,
            highMagic,
            consistentCadence,
            hiscoreProfile.getMagicLevel(),
            hiscoreProfile.getNonMagicSkillsAboveThreshold(config.getNonMagicSkillThreshold()),
            hiscoreProfile.getNonMagicTotalLevel(),
            matureAccountSuppressed,
            hiscoreProfile.getClueScrollCompletions(),
            hiscoreProfile.getCollectionLogItems(),
            hiscoreProfile.getClueAndCollectionLogTotal(),
            clueCollectionActivitySuppressed,
            hiscoreProfile.getStatusLabel(),
            evidence.getWorld(),
            evidence.getDistance(),
            evidence.getWeaponId(),
            evidence.getLastSeenMillis(),
            new ScoreBreakdown(
                staffScore,
                behaviorScore,
                hiscoreScore,
                highMagicScore,
                cadenceScore,
                matureAccountPenalty,
                clueCollectionPenalty,
                castGatePassed,
                staffGatePassed,
                detectionGatePassed
            )
        );
    }

    private boolean staffMatches(PlayerEvidence evidence, DetectorConfigSnapshot config)
    {
        return config.isIncludeFireRuneStaves()
            ? evidence.hasFireRuneProvider()
            : evidence.hasBasicFireStaff();
    }
}
