package store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ShardedCacheStore.
 * Validates core CRUD operations, Least Recently Used (LRU) eviction policies,
 * and Time-To-Live (TTL) expirations within a sharded environment.
 */
public class ShardedCacheTest {

    private KeyValueStore store;

    /**
     * Initializes a storage engine before each test.
     * The capacity is deliberately restricted to 3 items on a single shard
     * to predictably trigger the LRU eviction mechanics.
     */
    @BeforeEach
    public void setUp() {
        store = new ShardedCacheStore(3, 1);
    }

    /**
     * Tests basic storage, retrieval, and existence verification of key-value pairs.
     */
    @Test
    public void testBasicSetAndGet() {
        store.set("user:1", "Alice");

        assertEquals("Alice", store.get("user:1"));
        assertTrue(store.exists("user:1"));
    }

    /**
     * Validates the LRU eviction policy.
     * Ensures that when the store reaches maximum capacity, the least recently
     * accessed item is evicted while recently retrieved items are preserved.
     */
    @Test
    public void testLruEviction() {
        store.set("A", "1");
        store.set("B", "2");
        store.set("C", "3");

        store.get("A");

        store.set("D", "4");

        assertNotNull(store.get("A"));
        assertNull(store.get("B"));
        assertNotNull(store.get("C"));
        assertNotNull(store.get("D"));
    }

    /**
     * Tests the Time-To-Live (TTL) expiration mechanism.
     * Verifies that a key remains accessible while valid and becomes completely
     * inaccessible and purged once its designated lifetime elapses.
     *
     * @throws InterruptedException If the thread sleep used for time simulation is interrupted.
     */
    @Test
    public void testTtlExpiration() throws InterruptedException {
        store.setEx("tempKey", "temporaryValue", 1);

        assertEquals("temporaryValue", store.get("tempKey"));
        assertTrue(store.ttl("tempKey") > 0);

        Thread.sleep(1100);

        assertNull(store.get("tempKey"));
        assertFalse(store.exists("tempKey"));
        assertEquals(-2, store.ttl("tempKey"));
    }

    /**
     * Verifies the deletion of existing keys and ensures that subsequent
     * deletion attempts on the same or non-existent keys safely return false.
     */
    @Test
    public void testDelete() {
        store.set("keyToDelete", "value");

        assertTrue(store.delete("keyToDelete"));
        assertNull(store.get("keyToDelete"));
        assertFalse(store.delete("keyToDelete"));
    }
}