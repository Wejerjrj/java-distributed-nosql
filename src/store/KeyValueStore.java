package store;

import java.util.List;

/**
 * Defines the contract for the key-value storage engine.
 */
public interface KeyValueStore {

    /**
     * Inserts or updates a key-value pair in the store.
     *
     * @param key   The key to insert or update.
     * @param value The value to associate with the key.
     */
    void set(String key, String value);

    /**
     * Inserts or updates a key-value pair with a time-to-live (TTL) expiration.
     *
     * @param key     The key to insert or update.
     * @param value   The value to associate with the key.
     * @param seconds The time-to-live in seconds before the key expires.
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
     * Deletes the specified key from the store.
     *
     * @param key The key to remove.
     * @return true if the key was successfully removed, false otherwise.
     */
    boolean delete(String key);

    /**
     * Checks if a key exists in the database.
     *
     * @param key The key to check.
     * @return true if the key exists and is not expired, false otherwise.
     */
    boolean exists(String key);

    /**
     * Atomically increments the integer value of a key by 1.
     * If the key does not exist, it is initialized to 0 before incrementing.
     *
     * @param key The key to increment.
     * @return The new value after incrementing.
     * @throws NumberFormatException if the existing value cannot be parsed as an integer.
     */
    Long incr(String key);

    /**
     * Returns the remaining time to live of a key that has a timeout.
     *
     * @param key The key to check.
     * @return The remaining TTL in seconds, -1 if the key exists but has no TTL, or -2 if the key does not exist.
     */
    long ttl(String key);

    /**
     * Removes all keys from the database and clears all secondary indexes.
     */
    void flushAll();

    /**
     * Retrieves all keys currently stored in the database.
     *
     * @return A list containing all active keys.
     */
    List<String> getAllKeys();

    /**
     * Finds all keys that start with the specified prefix.
     *
     * @param prefix The prefix string to search for.
     * @return A list of matching keys in lexicographical order.
     */
    List<String> prefixMatch(String prefix);

    /**
     * Finds all keys within the specified lexicographical range.
     *
     * @param start The inclusive starting key of the range.
     * @param end   The inclusive ending key of the range.
     * @return A list of keys falling within the specified range.
     */
    List<String> range(String start, String end);
}