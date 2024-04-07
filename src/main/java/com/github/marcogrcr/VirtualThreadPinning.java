package com.github.marcogrcr;

import com.github.marcogrcr.util.Delay;
import com.github.marcogrcr.util.Measure;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** This class shows how thread pinning can affect throughput. */
public class VirtualThreadPinning {
    private static final List<Object> LOCKS = List.of(new Object(), new Object(), new Object());

    public static void main(final String[] args) {
        // ensure platform thread pool for virtual threads is of size 1
        System.setProperty("jdk.virtualThreadScheduler.parallelism", "1");
        System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", "1");

        // takes ~2s because: 1/ each task takes ~1s, 2/ two platform threads execute the tasks
        var duration = run(false, Executors.newFixedThreadPool(2));
        System.out.printf("[NoSynchronized/TwoThreads] took: %s%n%n", duration);

        // takes ~2s because: 1/ each task takes ~1s, 2/ two platform threads execute the tasks
        duration = run(true, Executors.newFixedThreadPool(2));
        System.out.printf("[Synchronized/TwoThreads] took: %s%n%n", duration);

        // takes ~1s because: 1/ each task takes ~1s, 2/ one platform thread executes the tasks, 3/ no thread pinning
        duration = run(false, Executors.newVirtualThreadPerTaskExecutor());
        System.out.printf("[NoSynchronized/VirtualThreads] took: %s%n%n", duration);

        // takes ~3s because: 1/ each task takes ~1s, 2/ one platform thread executes the tasks, 3/ with thread pinning
        duration = run(true, Executors.newVirtualThreadPerTaskExecutor());
        System.out.printf("[Synchronized/VirtualThreads] took: %s%n%n", duration);
    }

    private static Duration run(final boolean withSynchronized, final ExecutorService executor) {
        try (executor) {
            final Runnable task = () -> {
                // simulate I/O that takes 1 second to complete
                Delay.blocking(Duration.ofSeconds(1));
                System.out.printf("[%s] Done!%n", Thread.currentThread());
            };

            return Measure.duration(() -> {
                // start three virtual threads that each take ~1s to complete
                final var futures = new ArrayList<Future<?>>();
                for (var i = 0; i < 3; ++i) {
                    final var lock = LOCKS.get(i);
                    futures.add(
                            executor.submit(() -> {
                                if (withSynchronized) {
                                    synchronized (lock) {
                                        task.run();
                                    }
                                } else {
                                    task.run();
                                }
                            })
                    );
                }

                // wait for all the threads to finish
                for (final var future : futures) {
                    future.get();
                }
            });
        }
    }
}
