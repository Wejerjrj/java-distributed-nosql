package store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests validating the Write-Ahead Logging (WAL) mechanisms.
 * Ensures durability, crash recovery integrity, handling of corrupted log files,
 * and automatic background compaction.
 */
public class WalPersistenceTest {

    private static final String TEST_LOG_FILE = "test_recovery.log";
    private WalDecorator walStore;
    private KeyValueStore memoryStore;

    /**
     * Initializes a fresh memory store wrapped with a WAL decorator
     * bound to a temporary test file.
     */
    @BeforeEach
    public void setUp() {
        memoryStore = new ShardedCacheStore(10000, 16);
        walStore = new WalDecorator(memoryStore, TEST_LOG_FILE);
    }

    /**
     * Cleans up resources by closing the WAL and deleting the test log file.
     *
     * @throws IOException If an I/O error occurs during file deletion.
     */
    @AfterEach
    public void tearDown() throws IOException {
        if (walStore != null) {
            walStore.close();
        }

        File file = new File(TEST_LOG_FILE);
        if (file.exists()) {
            Files.delete(file.toPath());
        }
    }

    /**
     * Tests the standard crash recovery process.
     * Validates that pending asynchronous writes are safely flushed upon closing
     * and successfully restored into a new store instance.
     *
     * @throws InterruptedException If the thread is interrupted during recovery.
     */
    @Test
    public void testCrashRecoveryWithAsyncQueue() throws InterruptedException {
        walStore.set("user:99", "John Doe");
        walStore.incr("visits");
        walStore.setEx("temp", "data", 10);

        walStore.close();

        KeyValueStore newMemoryStore = new ShardedCacheStore(10000, 16);
        WalDecorator recoveredStore = new WalDecorator(newMemoryStore, TEST_LOG_FILE);

        recoveredStore.replayLog();

        assertEquals("John Doe", recoveredStore.get("user:99"));
        assertEquals("1", recoveredStore.get("visits"));
        assertEquals("data", recoveredStore.get("temp"));
        assertTrue(recoveredStore.ttl("temp") > 0);

        recoveredStore.close();
    }

    /**
     * Simulates a power failure during a binary write operation.
     * Ensures that the EOFException is caught during replay and that
     * previously persisted data remains intact without crashing the engine.
     *
     * @throws IOException          If an I/O error occurs during file manipulation.
     * @throws InterruptedException If the thread is interrupted.
     */
    @Test
    public void testTruncatedBinaryWalRecovery() throws IOException, InterruptedException {
        walStore.set("valid1", "data1");
        walStore.close();

        // Simulate a mid-write crash: declare 3 arguments but only write 2
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(TEST_LOG_FILE, true))) {
            out.writeInt(3);
            out.writeUTF("SET");
            out.writeUTF("corrupted_key");
        }

        KeyValueStore newMemoryStore = new ShardedCacheStore(10000, 16);
        WalDecorator recoveredStore = new WalDecorator(newMemoryStore, TEST_LOG_FILE);

        recoveredStore.replayLog();

        assertEquals("data1", recoveredStore.get("valid1"));
        assertFalse(recoveredStore.exists("corrupted_key"));

        recoveredStore.close();
    }

    /**
     * Verifies that the WAL automatically triggers a background file compaction
     * once the operation threshold is exceeded to prevent unbounded disk growth.
     *
     * @throws InterruptedException If the thread sleep is interrupted.
     */
    @Test
    public void testAutomaticWalCompaction() throws InterruptedException {
        for (int i = 0; i < 10050; i++) {
            walStore.set("key:" + i, "value");
        }

        // Allow time for the background compaction thread to finish swapping files
        Thread.sleep(1000);
        walStore.close();

        KeyValueStore newMemoryStore = new ShardedCacheStore(10000, 16);
        WalDecorator recoveredStore = new WalDecorator(newMemoryStore, TEST_LOG_FILE);

        recoveredStore.replayLog();

        assertEquals("value", recoveredStore.get("key:10000"));

        recoveredStore.close();
    }
}