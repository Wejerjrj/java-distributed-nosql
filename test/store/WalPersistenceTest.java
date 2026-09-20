package store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Write-Ahead Log (WAL) to ensure data durability,
 * crash recovery, corruption handling, and log compaction (Snapshotting).
 */
public class WalPersistenceTest {

    private static final String TEST_LOG_FILE = "test_recovery.log";
    private WalDecorator walStore;
    private KeyValueStore memoryStore;

    @BeforeEach
    public void setUp() {
        memoryStore = new LruCacheStore(100);
        walStore = new WalDecorator(memoryStore, TEST_LOG_FILE);
    }

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

    @Test
    public void testCrashRecovery() {
        walStore.set("user:99", "John Doe");
        walStore.incr("visits");
        walStore.incr("visits");
        walStore.setEx("temp", "data", 10);
        walStore.delete("user:99");

        KeyValueStore newMemoryStore = new LruCacheStore(100);
        WalDecorator recoveredStore = new WalDecorator(newMemoryStore, TEST_LOG_FILE);

        recoveredStore.replayLog();

        assertFalse(recoveredStore.exists("user:99"));
        assertEquals("2", recoveredStore.get("visits"));
        assertEquals("data", recoveredStore.get("temp"));
        assertTrue(recoveredStore.ttl("temp") > 0);

        recoveredStore.close();
    }

    @Test
    public void testFlushAllRecovery() {
        walStore.set("keep", "this");
        walStore.flushAll();
        walStore.set("new_key", "active");

        KeyValueStore newMemoryStore = new LruCacheStore(100);
        WalDecorator recoveredStore = new WalDecorator(newMemoryStore, TEST_LOG_FILE);

        recoveredStore.replayLog();

        assertFalse(recoveredStore.exists("keep"));
        assertEquals("active", recoveredStore.get("new_key"));

        recoveredStore.close();
    }

    @Test
    public void testCorruptedWalEntryIgnored() throws IOException {
        walStore.set("valid1", "data1");
        walStore.close();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TEST_LOG_FILE, true))) {
            writer.write("CORRUPTED_LINE_WITHOUT_MEANING");
            writer.newLine();
            writer.write("SET_EX invalid_args 10");
            writer.newLine();
        }

        walStore = new WalDecorator(memoryStore, TEST_LOG_FILE);
        walStore.set("valid2", "data2");

        KeyValueStore newMemoryStore = new LruCacheStore(100);
        WalDecorator recoveredStore = new WalDecorator(newMemoryStore, TEST_LOG_FILE);

        recoveredStore.replayLog();

        assertEquals("data1", recoveredStore.get("valid1"));
        assertEquals("data2", recoveredStore.get("valid2"));

        recoveredStore.close();
    }

    @Test
    public void testWalCompaction() {
        walStore.set("key1", "value1");
        walStore.set("key2", "value2");
        walStore.set("key1", "value1_updated");
        walStore.delete("key2");
        walStore.setEx("key3", "temp_value", 500);

        walStore.compact();

        walStore.set("key4", "value4");

        KeyValueStore newMemoryStore = new LruCacheStore(100);
        WalDecorator recoveredStore = new WalDecorator(newMemoryStore, TEST_LOG_FILE);

        recoveredStore.replayLog();

        assertEquals("value1_updated", recoveredStore.get("key1"));
        assertFalse(recoveredStore.exists("key2"));
        assertEquals("temp_value", recoveredStore.get("key3"));
        assertTrue(recoveredStore.ttl("key3") > 0);
        assertEquals("value4", recoveredStore.get("key4"));

        recoveredStore.close();
    }
}