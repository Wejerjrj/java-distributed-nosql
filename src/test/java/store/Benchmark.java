package store;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A standalone benchmarking tool designed to measure the throughput and latency
 * of the KeyValueStore engine under highly concurrent workloads.
 */
public class Benchmark {

    private static final int NUM_THREADS = 100;
    private static final int NUM_OPERATIONS_PER_THREAD = 10000;
    private static final int TOTAL_OPERATIONS = NUM_THREADS * NUM_OPERATIONS_PER_THREAD;

    /**
     * Executes the benchmarking suite.
     * Initializes the storage engine and triggers both write and read performance tests.
     *
     * @param args Command line arguments (unused).
     * @throws InterruptedException If the main thread is interrupted while waiting for worker threads.
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== IN-MEMORY DATABASE BENCHMARK ===");
        System.out.println("Concurrency Level : " + NUM_THREADS + " threads");
        System.out.println("Total Operations  : " + TOTAL_OPERATIONS);
        System.out.println("-------------------------------------------------");

        // The store. prefix is redundant since we are inside the store package
        KeyValueStore store = new ShardedCacheStore(TOTAL_OPERATIONS, 16);

        runWriteBenchmark(store);
        runReadBenchmark(store);

        System.out.println("-------------------------------------------------");
        System.out.println("Benchmark completed.");
    }

    /**
     * Simulates a highly concurrent write-heavy workload.
     * Measures the time required for the thread pool to execute a fixed number of SET operations
     * and calculates the resulting throughput.
     *
     * @param store The storage engine being tested.
     * @throws InterruptedException If the thread is interrupted while awaiting the countdown latch.
     */
    private static void runWriteBenchmark(KeyValueStore store) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            executor.execute(() -> {
                for (int j = 0; j < NUM_OPERATIONS_PER_THREAD; j++) {
                    store.set("key:" + threadId + ":" + j, "payload_data");
                }
                latch.countDown();
            });
        }

        latch.await();
        long endTime = System.currentTimeMillis();
        executor.shutdown();

        long durationMs = endTime - startTime;
        if (durationMs == 0) durationMs = 1;

        double opsPerSec = (TOTAL_OPERATIONS / (double) durationMs) * 1000;

        System.out.printf("WRITE (SET) Throughput : %,.2f ops/sec (Time: %d ms)%n", opsPerSec, durationMs);
    }

    /**
     * Simulates a highly concurrent read-heavy workload.
     * Measures the time required for the thread pool to execute a fixed number of GET operations
     * and calculates the resulting throughput.
     *
     * @param store The storage engine being tested.
     * @throws InterruptedException If the thread is interrupted while awaiting the countdown latch.
     */
    private static void runReadBenchmark(KeyValueStore store) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            executor.execute(() -> {
                for (int j = 0; j < NUM_OPERATIONS_PER_THREAD; j++) {
                    store.get("key:" + threadId + ":" + j);
                }
                latch.countDown();
            });
        }

        latch.await();
        long endTime = System.currentTimeMillis();
        executor.shutdown();

        long durationMs = endTime - startTime;
        if (durationMs == 0) durationMs = 1;

        double opsPerSec = (TOTAL_OPERATIONS / (double) durationMs) * 1000;

        System.out.printf("READ (GET) Throughput  : %,.2f ops/sec (Time: %d ms)%n", opsPerSec, durationMs);
    }
}