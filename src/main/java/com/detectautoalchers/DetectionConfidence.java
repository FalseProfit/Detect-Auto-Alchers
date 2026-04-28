package com.detectautoalchers;

enum DetectionConfidence
{
    NONE("none"),
    MODERATE("moderate"),
    HIGH("high");

    private final String label;

    DetectionConfidence(String label)
    {
        this.label = label;
    }

    String getLabel()
    {
        return label;
    }

    static DetectionConfidence fromScore(
        int score,
        boolean gatePassed,
        int moderateThreshold,
        int highThreshold)
    {
        if (!gatePassed || score < moderateThreshold)
        {
            return NONE;
        }

        return score >= highThreshold ? HIGH : MODERATE;
    }
}
