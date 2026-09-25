package store;

/**
 * Represents a node within the doubly linked list used for LRU cache management
 * and TTL expiration tracking within a cache shard.
 */
public class Node {

    String key;
    String value;
    Node prev;
    Node next;

    /**
     * Absolute epoch timestamp in milliseconds when this node expires.
     * A value of 0 indicates no expiration.
     */
    long expireAt;

    /**
     * Constructs a new cache node.
     *
     * @param key   The key identifier.
     * @param value The string payload.
     */
    public Node(String key, String value) {
        this.key = key;
        this.value = value;
        this.expireAt = 0; // 0 implies persistent by default
    }
}