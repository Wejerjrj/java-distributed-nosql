package store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * High-concurrency stress tests to validate thread-safety and lock integrity.
 */
public class ConcurrencyTest {

    private KeyValueStore store;

    @BeforeEach
    public void setUp() {
        store = new LruCacheStore(10000);
    }

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

        // Wait for all 1000 threads to complete their execution
        latch.await();
        executorService.shutdown();

        // If the ReadWriteLock is flawed, data race conditions will result in a number lower than 1000
        assertEquals("1000", store.get("page_views"));
    }
}