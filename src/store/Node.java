package store;

/**
 * Represents a node within the doubly linked list used for LRU cache management.
 */
public class Node {

    String key;
    String value;
    Node prev;
    Node next;
    long expireAt;

    /**
     * Constructs a new Node.
     *
     * @param key   The key stored in the node.
     * @param value The value stored in the node.
     */
    public Node(String key, String value) {
        this.key = key;
        this.value = value;
        this.expireAt = 0;
    }
}