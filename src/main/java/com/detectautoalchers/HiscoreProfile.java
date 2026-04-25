package com.detectautoalchers;

final class HiscoreProfile
{
    enum Status
    {
        UNKNOWN,
        PENDING,
        FOUND,
        NOT_FOUND,
        ERROR
    }

    private static final HiscoreProfile UNKNOWN = new HiscoreProfile(Status.UNKNOWN, -1, -1, false);
    private static final HiscoreProfile PENDING = new HiscoreProfile(Status.PENDING, -1, -1, false);
    private static final HiscoreProfile NOT_FOUND = new HiscoreProfile(Status.NOT_FOUND, -1, -1, false);
    private static final HiscoreProfile ERROR = new HiscoreProfile(Status.ERROR, -1, -1, false);

    private final Status status;
    private final int magicLevel;
    private final int nonMagicSkillsAboveThreshold;
    private final boolean magicDominant;

    private HiscoreProfile(Status status, int magicLevel, int nonMagicSkillsAboveThreshold, boolean magicDominant)
    {
        this.status = status;
        this.magicLevel = magicLevel;
        this.nonMagicSkillsAboveThreshold = nonMagicSkillsAboveThreshold;
        this.magicDominant = magicDominant;
    }

    static HiscoreProfile unknown()
    {
        return UNKNOWN;
    }

    static HiscoreProfile pending()
    {
        return PENDING;
    }

    static HiscoreProfile notFound()
    {
        return NOT_FOUND;
    }

    static HiscoreProfile error()
    {
        return ERROR;
    }

    static HiscoreProfile found(int magicLevel, int nonMagicSkillsAboveThreshold, boolean magicDominant)
    {
        return new HiscoreProfile(Status.FOUND, magicLevel, nonMagicSkillsAboveThreshold, magicDominant);
    }

    Status getStatus()
    {
        return status;
    }

    int getMagicLevel()
    {
        return magicLevel;
    }

    int getNonMagicSkillsAboveThreshold()
    {
        return nonMagicSkillsAboveThreshold;
    }

    boolean isMagicDominant()
    {
        return status == Status.FOUND && magicDominant;
    }

    boolean isTerminalSuccess()
    {
        return status == Status.FOUND;
    }

    String getStatusLabel()
    {
        switch (status)
        {
            case PENDING:
                return "pending";
            case FOUND:
                return "found";
            case NOT_FOUND:
                return "not found";
            case ERROR:
                return "error";
            case UNKNOWN:
            default:
                return "unknown";
        }
    }
}
