package store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests validating the integrity and performance of secondary indexes (Trie and AVL Tree).
 */
public class IndexTest {

    private KeyValueStore store;

    @BeforeEach
    public void setUp() {
        store = new LruCacheStore(100);
        store.set("apple", "1");
        store.set("app", "2");
        store.set("application", "3");
        store.set("banana", "4");
        store.set("batman", "5");
    }

    @Test
    public void testPrefixMatch() {
        List<String> appMatches = store.prefixMatch("app");
        assertEquals(3, appMatches.size());
        assertTrue(appMatches.contains("app"));
        assertTrue(appMatches.contains("apple"));
        assertTrue(appMatches.contains("application"));

        List<String> batMatches = store.prefixMatch("bat");
        assertEquals(1, batMatches.size());
        assertEquals("batman", batMatches.get(0));
    }

    @Test
    public void testRangeQuery() {
        List<String> rangeMatches = store.range("apple", "banana");

        assertEquals(3, rangeMatches.size());
        assertTrue(rangeMatches.contains("apple"));
        assertTrue(rangeMatches.contains("application"));
        assertTrue(rangeMatches.contains("banana"));
        assertFalse(rangeMatches.contains("app"));
        assertFalse(rangeMatches.contains("batman"));
    }

    @Test
    public void testIndexClearingOnFlushAll() {
        store.flushAll();

        assertTrue(store.getAllKeys().isEmpty());
        assertTrue(store.prefixMatch("app").isEmpty());
        assertTrue(store.range("a", "z").isEmpty());
    }

    @Test
    public void testIndexSyncOnDelete() {
        store.delete("apple");

        List<String> appMatches = store.prefixMatch("app");
        assertFalse(appMatches.contains("apple"));

        List<String> rangeMatches = store.range("a", "z");
        assertFalse(rangeMatches.contains("apple"));
    }
}