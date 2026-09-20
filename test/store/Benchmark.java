package store;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A standalone benchmarking tool to measure the throughput and latency
 * of the KeyValueStore engine under highly concurrent workloads.
 */
public class Benchmark {

    private static final int NUM_THREADS = 100;
    private static final int NUM_OPERATIONS_PER_THREAD = 10000;
    private static final int TOTAL_OPERATIONS = NUM_THREADS * NUM_OPERATIONS_PER_THREAD;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== IN-MEMORY DATABASE BENCHMARK ===");
        System.out.println("Concurrency Level : " + NUM_THREADS + " threads");
        System.out.println("Total Operations  : " + TOTAL_OPERATIONS);
        System.out.println("-------------------------------------------------");

        KeyValueStore store = new LruCacheStore(TOTAL_OPERATIONS);

        runWriteBenchmark(store);
        runReadBenchmark(store);

        System.out.println("-------------------------------------------------");
        System.out.println("Benchmark completed.");
    }

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