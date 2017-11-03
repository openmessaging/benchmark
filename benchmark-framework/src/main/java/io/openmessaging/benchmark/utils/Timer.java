package io.openmessaging.benchmark.utils;

import java.util.concurrent.TimeUnit;

public class Timer {
    private final long startTime;

    public Timer() {
        startTime = System.nanoTime();
    }

    public double elapsedMillis() {
        long now = System.nanoTime();
        return (now - startTime) / (double) TimeUnit.MILLISECONDS.toNanos(1);
    }
}
