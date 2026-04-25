package com.detectautoalchers;

final class AlchObservation
{
    private final long observedAtMillis;
    private final int tick;
    private final String source;
    private final int id;

    AlchObservation(long observedAtMillis, int tick, String source, int id)
    {
        this.observedAtMillis = observedAtMillis;
        this.tick = tick;
        this.source = source;
        this.id = id;
    }

    long getObservedAtMillis()
    {
        return observedAtMillis;
    }

    int getTick()
    {
        return tick;
    }

    String getSource()
    {
        return source;
    }

    int getId()
    {
        return id;
    }
}
