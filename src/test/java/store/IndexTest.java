package store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests validating the correctness of the secondary indexes (Trie and AVL Tree)
 * through the primary KeyValueStore interface. Ensures that index structures remain
 * strictly synchronized with the core data.
 */
public class IndexTest {

    private KeyValueStore store;

    /**
     * Initializes the storage engine and populates it with a predefined dataset
     * to serve as a baseline for the indexing tests.
     */
    @BeforeEach
    public void setUp() {
        store = new ShardedCacheStore(100, 16);
        store.set("apple", "1");
        store.set("app", "2");
        store.set("application", "3");
        store.set("banana", "4");
        store.set("batman", "5");
    }

    /**
     * Verifies that the Trie index correctly retrieves keys sharing a specific string prefix.
     */
    @Test
    public void testPrefixMatch() {
        List<String> appMatches = store.prefixMatch("app", 100);

        assertEquals(3, appMatches.size());
        assertTrue(appMatches.contains("app"));
        assertTrue(appMatches.contains("apple"));
        assertTrue(appMatches.contains("application"));

        List<String> batMatches = store.prefixMatch("bat", 100);

        assertEquals(1, batMatches.size());
        assertEquals("batman", batMatches.get(0));
    }

    /**
     * Validates that the AVL Tree index accurately fetches keys falling within
     * a specified lexicographical range.
     */
    @Test
    public void testRangeQuery() {
        List<String> rangeMatches = store.range("apple", "banana", 100);

        assertEquals(3, rangeMatches.size());
        assertTrue(rangeMatches.contains("apple"));
        assertTrue(rangeMatches.contains("application"));
        assertTrue(rangeMatches.contains("banana"));

        assertFalse(rangeMatches.contains("app"));
        assertFalse(rangeMatches.contains("batman"));
    }

    /**
     * Ensures that flushing the database completely clears all secondary indexes
     * to prevent memory leaks or the retrieval of ghost data.
     */
    @Test
    public void testIndexClearingOnFlushAll() {
        store.flushAll();

        assertTrue(store.getAllKeys().isEmpty());
        assertTrue(store.prefixMatch("app", 100).isEmpty());
        assertTrue(store.range("a", "z", 100).isEmpty());
    }

    /**
     * Verifies that deleting a key from the primary store synchronously removes it
     * from all secondary indexes to maintain strict data consistency.
     */
    @Test
    public void testIndexSyncOnDelete() {
        store.delete("apple");

        List<String> appMatches = store.prefixMatch("app", 100);
        assertFalse(appMatches.contains("apple"));

        List<String> rangeMatches = store.range("a", "z", 100);
        assertFalse(rangeMatches.contains("apple"));
    }
}