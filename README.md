# Java 21 Virtual Threads

This repository provides the necessary context to understand what Java 21 [Virtual Threads] are, how they work, and what makes them unique.
It also provides a few examples to understand them more in depth.

## Concurrent programming

In the context of programming, performing more than one task at a time is known as concurrent programming.
Probably the most popular concurrent program example is an HTTP server.
HTTP servers await incoming requests and concurrently process them in order to handle more than one request at a time. 

## Java's `Thread`

Since its initial release, Java has provided the ability to write concurrent programs via the [Thread] class.
Historically, `Thread` instances have been an expensive object to create and maintain since they have a 1:1 mapping with OS (operating system) threads.
As a consequence of this 1:1 mapping, each `Thread` instance takes a significant amount of memory and requires the OS scheduler to perform a context switch in order for it to execute.

## Thread pools

To optimize this cost, Java HTTP servers have traditionally processed HTTP requests using thread pools.
Thread pools are a collection of reusable `Thread` objects that avoid the cost associated with creating a `Thread` object for each processing task.
Thread pools can have a static or dynamic size, but in general they specify an upper bound to avoid depleting compute and memory resources.
Thus, in traditional Java HTTP servers, the maximum number of requests that can be processed concurrently is bound to the maximum size its associated thread pool.
Any incoming requests that exceed this limit would either have to be buffered while waiting for a `Thread` to become available or be rejected altogether.

The following example shows two states that an HTTP server can be in: 

**State 1**

```plain
| HTTP client |                   HTTP server                   |
|=============|=================================================|
|             |      HTTP requests       | Thread Pool (max: 3) |
|             |==========================|======================|
|             | Buffer (max: 2) | Active |   Idle   |  Active   |
|             |==========================|==========|===========|
|      o------|-----------------|-->r1<--|----------|----t1     |
|      o------|-----------------|-->r2<--|----------|----t2     |
|             |                 |        |    t3    |           |
```

- The request buffer is empty.
- There are two active requests: `r1` and `r2`.
- There are two active threads: `t1` and `t2`.
- The is one idle thread: `t3`.

Then, some time after **state 1**...

**State 2**

```plain
| HTTP client |                   HTTP server                   |
|=============|=================================================|
|             |      HTTP requests       | Thread Pool (max: 3) |
|             |==========================|======================|
|             | Buffer (max: 2) | Active |   Idle   |  Active   |
|             |==========================|==========|===========|
|      o------|-----------------|-->r3<--|----------|----t1     |
|      o------|-----------------|-->r2<--|----------|----t2     |
|      o------|-----------------|-->r4<--|----------|----t3     |
|      o------|------>r5        |        |          |           |
|      o------|------>r6        |        |          |           |
|      o-----X|                 |        |          |           |
```

- There are two buffered requests: `r5` and `r6`.
- There are three active requests: `r2`, `r3` and `r4`.
- There are three active threads: `t1`, `t2` and `t3`.
- There are no idle threads.
- The HTTP server rejects an incoming request because the buffer is full.

## Synchronous programming

Most services exposed via HTTP spend most of the request processing time waiting for other things to complete.
This is known as IO-bound work (the opposite of CPU-bound work).
For example, while processing a request, a financial service may internally send an HTTP request to another service in order to obtain the exchange rate between two currencies and then wait for a response.
Historically, this waiting [BLOCKED] the `Thread` and prevented it from performing any other work (like processing a different HTTP request) until it was done waiting and it finished processing the HTTP request.
This programming model known as synchronous programming while easy to understand, it suffers from this major drawback.

## Asynchronous programming

To mitigate this problem, Java provides support for the asynchronous programming model.
Asynchronous programming allows to execute a task without blocking and provides a combination of the following:

- A mechanism to check the asynchronous execution status and obtain its result when complete.
  Java 5 introduced the [Future] interface to support this.
- A mechanism to provide a callback (i.e. a method) that will be invoked with the result of the asynchronous execution.
  Java 8 introduced the [CompletableFuture] class to support this.

While this solves the `Thread` blocking problem, it comes at the expense of a more complex programming model that's harder to read, write, test, debug and reason about.

It's worth noting that asynchronous tasks are not exempt from the `Thread` exhaustion problem as they're also executed by `Thread` instances.
For example, `CompletableFuture` async methods run by default in the [ForkJoinPool.commonPool()] (a special kind of thread pool).
This pool also has an upper bound, so it's critical that any waiting is performed in a non-blocking manner.
For example, this means using [CompletableFuture.delayedExecutor()] instead of [Thread.sleep()], Java 11's [HttpClient] instead of [HttpURLConnection], etc. 

## Virtual Threads

Java 21 introduced the concept of Virtual Threads which provide the best of both worlds: the simplicity of the synchronous programming model with the performance benefits of the asynchronous programming model.
Starting with Java 21 there are now two kinds of threads:

- **Platform Threads:** These are the pre-Java 21 "classic" `Thread` instances that have a 1:1 mapping with OS threads.
- **Virtual Threads:** These are lightweight `Thread` instances that only exist in the JVM (Java Virtual Machine).

Since Virtual Threads are also instances of the `Thread` class, they can be distinguished by invoking [Thread.isVirtual()].
Virtual Threads can be created in various ways including: [Thread.startVirtualThread()], [Thread.ofVirtual()], and [Executors.newVirtualThreadPerTaskExecutor()].

Virtual Threads cannot execute on their own. Instead, they must be "mounted" by a Platform Thread.
The Platform Thread that executes a mounted Virtual Thread become its "Carrier Thread".
Whenever a Virtual Thread invokes a blocking method like [Thread.sleep()], the Virtual Thread becomes `BLOCKED` and it's unmounted from its Carrier Thread.
As a result, the Platform Thread is "released" and becomes available for mounting any other Virtual Thread in the [RUNNABLE] state which has not been mounted by another Platform Thread.
Once the `BLOCKED` Virtual Thread is done waiting and becomes `RUNNABLE` again, it can be mounted by the same **or a different** Platform Thread to resume execution.
However, for all intents and purposes Virtual Threads are still `Thread` instances, which means that things like [ThreadLocal] will work as expected even if the underlying Platform Thread changes.

Virtual Threads are executed by an implicitly-created thread pool of Platform Threads akin to, but separate from `ForkJoinPool.commonPool()`.
Developers have little control over this pool other than the following system properties:

- `jdk.virtualThreadScheduler.parallelism`: The number of platform threads available for scheduling virtual threads. It defaults to the number of available processors. 
- `jdk.virtualThreadScheduler.maxPoolSize`: The maximum number of platform threads available to the scheduler. It defaults to 256. 

There is one major caveat when using Virtual Threads known as **thread pinning**.
When a Virtual Thread performs one of the following actions:
- Invoke a [native] method of a [foreign function].
- Invoke a blocking method while inside a [synchronized] block.

Then the Virtual Thread will "pin" to its Carrier Thread before becoming `BLOCKED` and preventing it from mounting any other Virtual Threads.
In other words, the underlying Platform Threads also becomes `BLOCKED`.
Therefore, in order to safeguard the throughput of Virtual Threads, it's critical that these actions are avoided in Virtual Threads.

## Examples

This repository contains the following examples.

- [VirtualThreadBasics.java]: Contains the basics on creating and starting Virtual Threads.
- [VirtualThreadsVsOtherApproaches.java]: Compares Virtual Threads with other concurrent programming approaches.
- [VirtualThreadsAreCheap.java]: Showcases how creating Virtual Threads is a cheap operation.
- [VirtualThreadPinning.java]: Demonstrates how thread pinning negatively affects throughput.
- [VirtualThreadLocal.java]: Displays how `ThreadLocal` works in Virtual Threads even when the Carrier Thread changes. 

[BLOCKED]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.State.html#BLOCKED
[CompletableFuture]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html
[CompletableFuture.delayedExecutor()]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html#delayedExecutor(long,java.util.concurrent.TimeUnit)
[Executors.newVirtualThreadPerTaskExecutor()]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html#newVirtualThreadPerTaskExecutor()
[ForkJoinPool.commonPool()]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html#commonPool()
[Future]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html
[HttpClient]: https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html
[HttpURLConnection]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/HttpURLConnection.html
[RUNNABLE]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.State.html#RUNNABLE
[Thread]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html
[ThreadLocal]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html
[Thread.isVirtual()]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#isVirtual()
[Thread.ofVirtual()]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#ofVirtual()
[Thread.startVirtualThread()]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#startVirtualThread(java.lang.Runnable)
[Thread.sleep()]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#sleep(long)
[Virtual Threads]: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
[VirtualThreadBasics.java]: https://github.com/marcogrcr/java21-virtual-threads/blob/main/src/main/java/com/github/marcogrcr/VirtualThreadBasics.java
[VirtualThreadLocal.java]: https://github.com/marcogrcr/java21-virtual-threads/blob/main/src/main/java/com/github/marcogrcr/VirtualThreadLocal.java
[VirtualThreadPinning.java]: https://github.com/marcogrcr/java21-virtual-threads/blob/main/src/main/java/com/github/marcogrcr/VirtualThreadPinning.java
[VirtualThreadsAreCheap.java]: https://github.com/marcogrcr/java21-virtual-threads/blob/main/src/main/java/com/github/marcogrcr/VirtualThreadsAreCheap.java
[VirtualThreadsVsOtherApproaches.java]: https://github.com/marcogrcr/java21-virtual-threads/blob/main/src/main/java/com/github/marcogrcr/VirtualThreadsVsOtherApproaches.java
[foreign function]: https://docs.oracle.com/en/java/javase/21/core/foreign-function-and-memory-api.html
[native]: https://docs.oracle.com/en/java/javase/21/docs/specs/jni/index.html
[synchronized]: https://docs.oracle.com/javase/tutorial/essential/concurrency/syncmeth.html
