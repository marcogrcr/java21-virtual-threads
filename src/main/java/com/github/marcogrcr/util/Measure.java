package com.github.marcogrcr.util;

import java.time.Duration;

/** Provides methods to perform measurements. */
public final class Measure {
    private Measure() {
    }

    /**
     * Measures the duration of a {@link Runnable}.
     * @param runnable The runnable to measure its duration.
     */
    public static Duration duration(final Runnable runnable) {
        final var start = System.nanoTime();
        try {
            runnable.run();
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
        final var end = System.nanoTime();

        return Duration.ofNanos(end - start);
    }

    /** Runnable inspired in {@link java.lang.Runnable} that throws {@link Throwable}. */
    @FunctionalInterface
    public interface Runnable {
        void run() throws Throwable;
    }
}
