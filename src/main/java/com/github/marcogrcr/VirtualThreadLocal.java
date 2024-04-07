package com.github.marcogrcr;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** This class demonstrates how {@link ThreadLocal} works across different Carrier Threads. */
public class VirtualThreadLocal {
    private final static ThreadLocal<String> THREAD_LOCAL = new ThreadLocal<>();
    private final static Semaphore MAIN_SEMAPHORE = new Semaphore(0);
    private final static Semaphore T1_SEMAPHORE = new Semaphore(0);
    private final static Semaphore T2_SEMAPHORE = new Semaphore(0);

    public static void main(final String[] args) throws Throwable {
        // ensure platform thread pool for virtual threads is of size 2
        System.setProperty("jdk.virtualThreadScheduler.parallelism", "2");
        System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", "2");

        // populate thread local in main thread
        System.out.printf("[%s] Value before set: %s%n", Thread.currentThread(), THREAD_LOCAL.get());
        THREAD_LOCAL.set("main");
        System.out.printf("[%s] Value after set: %s%n", Thread.currentThread(), THREAD_LOCAL.get());

        final var t1 = Thread.startVirtualThread(() -> {
            // populate thread local in t1
            System.out.printf("[ThreadOne/%s] Value before set: %s%n", Thread.currentThread(), THREAD_LOCAL.get());
            THREAD_LOCAL.set("t1");
            System.out.printf("[ThreadOne/%s] Value after set: %s%n", Thread.currentThread(), THREAD_LOCAL.get());

            // wait a second and unblock main: ensures t2's initial carrier thread is the same as t1's initial carrier thread
            CompletableFuture.runAsync(MAIN_SEMAPHORE::release, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));

            // block waiting for t2: this releases the carrier thread
            try {
                T1_SEMAPHORE.acquire();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }

            // value will still be "t1" even though this runs on a different carrier thread
            System.out.printf("[ThreadOne/%s] Value before exit: %s%n", Thread.currentThread(), THREAD_LOCAL.get());

            // unblock t2
            T2_SEMAPHORE.release();
        });

        // block waiting for t1
        MAIN_SEMAPHORE.acquire();

        final var t2 = Thread.startVirtualThread(() -> {
            // populate thread local in t2
            System.out.printf("[ThreadTwo/%s] Value before set: %s%n", Thread.currentThread(), THREAD_LOCAL.get());
            THREAD_LOCAL.set("t2");
            System.out.printf("[ThreadTwo/%s] Value after set: %s%n", Thread.currentThread(), THREAD_LOCAL.get());

            // unblock t1
            T1_SEMAPHORE.release();

            // pin the carrier thread: ensures t1's continuation runs in a different platform thread
            synchronized (new Object()) {
                try {
                    // block waiting for t1
                    T2_SEMAPHORE.acquire();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            System.out.printf("[ThreadTwo/%s] Value before exit: %s%n", Thread.currentThread(), THREAD_LOCAL.get());
        });

        // wait for the two threads to finish before exiting
        t1.join();
        t2.join();

        System.out.printf("[%s] Value before exit: %s%n", Thread.currentThread(), THREAD_LOCAL.get());
    }
}
