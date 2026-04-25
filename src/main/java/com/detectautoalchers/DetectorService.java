package com.detectautoalchers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class DetectorService
{
    private static final long STALE_MULTIPLIER = 2L;
    private final Map<String, PlayerEvidence> evidenceByName = new LinkedHashMap<>();

    synchronized void clear()
    {
        evidenceByName.clear();
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
        if (evidence == null || evidence.isHiscoreLookupInFlight())
        {
            return false;
        }

        HiscoreProfile profile = evidence.getHiscoreProfile();
        if (profile.isTerminalSuccess())
        {
            return false;
        }

        boolean candidate = staffMatches(evidence, config)
            || evidence.getObservationCount(nowMillis, config.getObservationWindowMillis()) > 0;
        if (!candidate)
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
            if (result != null && result.isSuspicious())
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

    synchronized boolean isSuspicious(String displayName)
    {
        PlayerEvidence evidence = evidenceByName.get(normalizeName(displayName));
        return evidence != null
            && evidence.getLastResult() != null
            && evidence.getLastResult().isSuspicious();
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
        boolean behaviorMatch = castCount >= config.getCastThreshold();
        HiscoreProfile hiscoreProfile = evidence.getHiscoreProfile();
        boolean magicDominant = config.isEnableHiscoreScoring() && hiscoreProfile.isMagicDominant();
        boolean consistentCadence = behaviorMatch && evidence.hasConsistentCadence(
            nowMillis,
            config.getObservationWindowMillis(),
            config.getCastThreshold()
        );

        if (staffMatch)
        {
            score += DetectorConfigSnapshot.STAFF_SCORE;
        }

        if (behaviorMatch)
        {
            score += DetectorConfigSnapshot.BEHAVIOR_SCORE;
        }

        if (magicDominant)
        {
            score += DetectorConfigSnapshot.HISCORE_SCORE;
        }

        if (consistentCadence)
        {
            score += DetectorConfigSnapshot.CADENCE_SCORE;
        }

        boolean staffGatePassed = !config.isRequireFireStaff() || staffMatch;
        boolean suspicious = staffGatePassed && score >= config.getSuspicionThreshold();

        return new SuspicionResult(
            evidence.getNormalizedName(),
            evidence.getDisplayName(),
            score,
            suspicious,
            castCount,
            staffMatch,
            behaviorMatch,
            magicDominant,
            consistentCadence,
            hiscoreProfile.getMagicLevel(),
            hiscoreProfile.getNonMagicSkillsAboveThreshold(),
            hiscoreProfile.getStatusLabel(),
            evidence.getWorld(),
            evidence.getDistance(),
            evidence.getWeaponId(),
            evidence.getLastSeenMillis()
        );
    }

    private boolean staffMatches(PlayerEvidence evidence, DetectorConfigSnapshot config)
    {
        return config.isIncludeFireRuneStaves()
            ? evidence.hasFireRuneProvider()
            : evidence.hasBasicFireStaff();
    }
}
