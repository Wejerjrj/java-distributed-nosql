package store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import store.KeyValueStore;
import store.ShardedCacheStore;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * High-concurrency stress tests for the storage engine.
 * Validates thread-safety, lock striping integrity, and the correct behavior
 * of the global read-write locks during intense simultaneous read/write operations.
 */
public class ConcurrencyTest {

    private KeyValueStore store;

    /**
     * Initializes a fresh sharded cache store before each test iteration.
     */
    @BeforeEach
    public void setUp() {
        store = new ShardedCacheStore(10000, 16);
    }

    /**
     * Validates the atomic integrity of the increment operation under high contention.
     * Ensures that concurrent writes to the same key are safely queued by the shard lock
     * without resulting in lost updates due to race conditions.
     *
     * @throws InterruptedException If the thread is interrupted while waiting for the latch.
     */
    @Test
    public void testAtomicIncrementUnderHighConcurrency() throws InterruptedException {
        int numberOfThreads = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        store.set("page_views", "0");

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.execute(() -> {
                try {
                    store.incr("page_views");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertEquals("1000", store.get("page_views"));
    }

    /**
     * Subjects the secondary indexes (Trie and AVL Tree) to heavy read/write contention.
     * Validates that the global ReadWriteLock properly synchronizes state without throwing
     * concurrent modification exceptions or causing deadlocks.
     *
     * @throws InterruptedException If the thread is interrupted while waiting for the latch.
     */
    @Test
    public void testIndexReadWriteLockUnderHeavyLoad() throws InterruptedException {
        int numThreads = 200;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < 50; i++) {
            store.set("key:" + i, "val");
        }

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.execute(() -> {
                try {
                    if (threadId % 4 == 0) {
                        store.set("new_key:" + threadId, "data");
                    } else if (threadId % 4 == 1) {
                        store.delete("key:" + (threadId % 50));
                    } else if (threadId % 4 == 2) {
                        store.prefixMatch("key:", 100);
                    } else {
                        store.range("a", "z", 100);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errorCount.get(), "No concurrency exceptions should be thrown during simultaneous index access.");
    }
}