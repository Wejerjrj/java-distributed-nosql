package store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for the CacheShard class.
 * Validates the core shard mechanics including LRU eviction policies,
 * TTL-based expirations, and atomic operations.
 */
public class CacheShardTest {

    private CacheShard shard;

    /**
     * Initializes a fresh CacheShard before each test.
     * The capacity is intentionally set low (3) to easily trigger and verify LRU eviction.
     */
    @BeforeEach
    public void setUp() {
        shard = new CacheShard(3);
    }

    /**
     * Tests the Least Recently Used (LRU) eviction strategy.
     * Verifies that accessing an element updates its position, and that
     * exceeding the capacity removes the least recently accessed element.
     */
    @Test
    public void testLruEviction_ShouldRemoveOldestElement() {
        shard.set("A", "1", 0);
        shard.set("B", "2", 0);
        shard.set("C", "3", 0);

        shard.get("A");

        shard.set("D", "4", 0);

        assertThat("Key A was accessed recently and should be retained", shard.get("A"), is(equalTo("1")));
        assertThat("Key B is the least recently used and should have been evicted", shard.get("B"), is(nullValue()));
        assertThat("Key C should be retained", shard.get("C"), is(equalTo("3")));
        assertThat("Key D was just inserted and should be retained", shard.get("D"), is(equalTo("4")));
    }

    /**
     * Tests the Time-To-Live (TTL) expiration mechanism.
     * Ensures that a key becomes completely inaccessible once its specified lifetime elapses.
     *
     * @throws InterruptedException If the thread sleep is interrupted during the delay simulation.
     */
    @Test
    public void testTtlExpiration_ShouldReturnNullAfterTimeElapsed() throws InterruptedException {
        shard.set("tempKey", "val", System.currentTimeMillis() + 100);

        assertThat("The key should exist immediately after insertion", shard.get("tempKey"), is(equalTo("val")));

        Thread.sleep(150);

        assertThat("The key should have expired and must return null", shard.get("tempKey"), is(nullValue()));
    }

    /**
     * Tests the atomic increment operation.
     * Verifies that a non-existent key is initialized to 1, and subsequent calls increment it.
     */
    @Test
    public void testIncrement_ShouldCreateOrIncreaseValue() {
        shard.incr("counter");
        assertThat("The initial increment should create the key and set its value to 1", shard.get("counter"), is(equalTo("1")));

        shard.incr("counter");
        assertThat("The value should be successfully incremented to 2", shard.get("counter"), is(equalTo("2")));
    }
}