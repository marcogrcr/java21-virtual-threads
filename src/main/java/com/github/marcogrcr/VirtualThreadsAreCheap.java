package com.github.marcogrcr;

import com.github.marcogrcr.util.Delay;
import com.github.marcogrcr.util.Measure;
import java.time.Duration;
import java.util.ArrayList;

/**
 * This class demonstrates how Virtual Threads are cheap to create.
 * Imagine running this code with Platform Threads, it won't be before long that your computer slows to a crawl!
 */
public class VirtualThreadsAreCheap {
    public static void main(final String[] args) {
        final var duration = Measure.duration(() -> {
            // create 100K Virtual Threads. Each threads blocks for 1 second.
            final var threads = new ArrayList<Thread>();
            for (var i = 0; i < 100_000; ++i) {
                threads.add(
                        // simulate I/O that takes 1 second to complete
                        Thread.startVirtualThread(() -> Delay.blocking(Duration.ofSeconds(1)))
                );
            }

            // wait for all the threads to finish
            for (final var thread : threads) {
                thread.join();
            }
        });

        System.out.printf("Done in %s%n", duration);
    }
}
