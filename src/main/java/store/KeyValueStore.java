package store;

import java.util.List;

/**
 * Defines the core contract for the distributed Key-Value storage engine.
 * Implementing classes must provide thread-safe operations and handle
 * indexing, expiration (TTL), and data eviction policies.
 */
public interface KeyValueStore {

    /**
     * Stores a key-value pair in the database.
     *
     * @param key   The unique identifier for the data.
     * @param value The string payload to store.
     */
    void set(String key, String value);

    /**
     * Stores a key-value pair with a specified Time-To-Live (TTL).
     *
     * @param key     The unique identifier for the data.
     * @param value   The string payload to store.
     * @param seconds The number of seconds until the key expires.
     */
    void setEx(String key, String value, int seconds);

    /**
     * Retrieves the value associated with the specified key.
     *
     * @param key The key to look up.
     * @return The associated value, or null if the key does not exist or has expired.
     */
    String get(String key);

    /**
     * Removes a key and its associated value from the database.
     *
     * @param key The key to remove.
     * @return True if the key was found and deleted, false otherwise.
     */
    boolean delete(String key);

    /**
     * Checks if a key exists in the database and is currently valid.
     *
     * @param key The key to verify.
     * @return True if the key exists and has not expired, false otherwise.
     */
    boolean exists(String key);

    /**
     * Atomically increments the numerical value associated with the given key by 1.
     * If the key does not exist, it is initialized to 1.
     *
     * @param key The key to increment.
     * @return The new incremented value.
     * @throws NumberFormatException if the existing value cannot be parsed as a number.
     */
    Long incr(String key);

    /**
     * Calculates the remaining time to live for a specific key.
     *
     * @param key The key to evaluate.
     * @return The remaining TTL in seconds, -1 if no TTL is set, or -2 if the key does not exist.
     */
    long ttl(String key);

    /**
     * Completely wipes all data, resetting the core cache and all secondary indexes.
     */
    void flushAll();

    /**
     * Retrieves a snapshot list of all active keys in the database.
     *
     * @return A list containing all active keys.
     */
    List<String> getAllKeys();

    /**
     * Performs a paginated prefix search utilizing the secondary index.
     *
     * @param prefix The string prefix to match against.
     * @param limit  The maximum number of keys to return.
     * @return A list of keys starting with the specified prefix.
     */
    List<String> prefixMatch(String prefix, int limit);

    /**
     * Performs a paginated lexicographical range query utilizing the secondary index.
     *
     * @param start The lower bound of the query (inclusive).
     * @param end   The upper bound of the query (inclusive).
     * @param limit The maximum number of keys to return.
     * @return A list of keys falling within the specified alphabetical range.
     */
    List<String> range(String start, String end, int limit);
}