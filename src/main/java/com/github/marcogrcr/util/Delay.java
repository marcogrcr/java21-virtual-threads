package com.github.marcogrcr.util;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** Provides methods to artificially delay an execution. */
public final class Delay {
    private static final Runnable NO_OP = () -> {};

    private Delay() {
    }

    /** Delays the execution of a thread by blocking it with {@link Thread#sleep}. */
    public static void blocking(final Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns a {@link CompletableFuture} that completes after the specified duration. */
    public static CompletableFuture<Void> nonBlocking(final Duration duration, final Executor executor) {
        return CompletableFuture.runAsync(
                NO_OP,
                CompletableFuture.delayedExecutor(duration.toNanos(), TimeUnit.NANOSECONDS, executor)
        );
    }
}
