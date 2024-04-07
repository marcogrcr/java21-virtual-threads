package com.github.marcogrcr;

import java.util.concurrent.Executors;

public class VirtualThreadBasics {
    public static void main(final String[] args) throws Throwable {
        // create and start a virtual thread
        var thread = Thread.startVirtualThread(() -> System.out.printf("Hello from: %s%n", Thread.currentThread()));
        thread.join();

        // create a virtual thread builder
        final var builder = Thread.ofVirtual().name("MyThread-", 0);

        // create and start a virtual thread through the builder
        thread = builder.start(() -> System.out.printf("Hello from: %s%n", Thread.currentThread()));
        thread.join();

        // create an un-started virtual thread through the builder
        thread = builder.unstarted(() -> System.out.printf("Hello from: %s%n", Thread.currentThread()));
        thread.start();
        thread.join();

        // create a thread factory from the builder
        final var factory = builder.factory();
        thread = factory.newThread(() -> System.out.printf("Hello from: %s%n", Thread.currentThread()));
        thread.start();
        thread.join();

        // create virtual threads using an executor
        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var future = executor.submit(() -> System.out.printf("Hello from: %s%n", Thread.currentThread()));
            future.get();
        }
    }
}
