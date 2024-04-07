package com.github.marcogrcr;

import com.github.marcogrcr.util.Delay;
import com.github.marcogrcr.util.Measure;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/** This class demonstrates how Virtual Threads compare to other approaches in terms of throughput. */
public class VirtualThreadsVsOtherApproaches {
    public static void main(final String[] args) {
        // ensure platform thread pool for virtual threads is of size 1
        System.setProperty("jdk.virtualThreadScheduler.parallelism", "1");
        System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", "1");

        // takes ~3s because: 1/ each task takes ~1s, 2/ one thread executes the tasks
        var duration = runThreeTasksBlocking(Executors.newSingleThreadExecutor());
        System.out.printf("[Blocking/OneThread] took: %s%n%n", duration);

        // takes ~2s because: 1/ each task takes ~1s, 2/ two threads execute the tasks
        duration = runThreeTasksBlocking(Executors.newFixedThreadPool(2));
        System.out.printf("[Blocking/TwoThreads] took: %s%n%n", duration);

        // takes ~1s because: 1/ each task takes ~1s, 2/ one thread executes the tasks, 3/ the thread is not blocked
        duration = runThreeTasksNonBlocking(Executors.newSingleThreadExecutor());
        System.out.printf("[NonBlocking/OneThread] took: %s%n%n", duration);

        // takes ~1s because: 1/ each task takes ~1s, 2/ one platform thread executes the tasks, 3/ the platform thread is not blocked
        duration = runThreeTasksBlocking(Executors.newVirtualThreadPerTaskExecutor());
        System.out.printf("[Blocking/VirtualThreads] took: %s%n%n", duration);
    }

    private static Duration runThreeTasksBlocking(final ExecutorService executor) {
        try (executor) {
            return runThreeTasks(() -> executor.submit(() -> {
                // simulate I/O that takes 1 second to complete
                Delay.blocking(Duration.ofSeconds(1));

                System.out.printf("[%s] Done!%n", Thread.currentThread());
            }));
        }
    }

    private static Duration runThreeTasksNonBlocking(final ExecutorService executor) {
        try (executor) {
            // simulate I/O that takes 1 second to complete
            return runThreeTasks(() -> Delay
                    .nonBlocking(Duration.ofSeconds(1), executor)
                    .thenRun(() -> System.out.printf("[%s] Done!%n", Thread.currentThread()))
            );
        }
    }

    private static Duration runThreeTasks(final Supplier<Future<?>> getFuture) {
        return Measure.duration(() -> {
            final var futures = new ArrayList<Future<?>>();
            for (var i = 0; i < 3; ++i) {
                futures.add(getFuture.get());
            }

            for (final var future : futures) {
                future.get();
            }
        });
    }
}
