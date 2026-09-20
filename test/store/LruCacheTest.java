package store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the LruCacheStore focusing on eviction logic, TTL, and basic CRUD operations.
 */
public class LruCacheTest {

    private KeyValueStore store;

    @BeforeEach
    public void setUp() {
        // Initialize a store with a strict capacity of 3 items for eviction testing
        store = new LruCacheStore(3);
    }

    @Test
    public void testBasicSetAndGet() {
        store.set("user:1", "Alice");
        assertEquals("Alice", store.get("user:1"));
        assertTrue(store.exists("user:1"));
    }

    @Test
    public void testLruEviction() {
        store.set("A", "1");
        store.set("B", "2");
        store.set("C", "3");

        // Access 'A' to make it the most recently used
        store.get("A");

        // Insert 'D', which exceeds capacity (3). 'B' should be evicted as the least recently used.
        store.set("D", "4");

        assertNotNull(store.get("A"));
        assertNull(store.get("B"));
        assertNotNull(store.get("C"));
        assertNotNull(store.get("D"));
    }

    @Test
    public void testTtlExpiration() throws InterruptedException {
        store.setEx("tempKey", "temporaryValue", 1);

        assertEquals("temporaryValue", store.get("tempKey"));
        assertTrue(store.ttl("tempKey") > 0);

        // Wait for 1.1 seconds to ensure the key expires
        Thread.sleep(1100);

        assertNull(store.get("tempKey"));
        assertFalse(store.exists("tempKey"));
        assertEquals(-2, store.ttl("tempKey"));
    }

    @Test
    public void testDelete() {
        store.set("keyToDelete", "value");
        assertTrue(store.delete("keyToDelete"));
        assertNull(store.get("keyToDelete"));
        assertFalse(store.delete("keyToDelete"));
    }
}