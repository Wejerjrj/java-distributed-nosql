package store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe memory segment representing a single shard of the overall database.
 * Utilizes a doubly linked list to enforce Least Recently Used (LRU) eviction policies,
 * and a Min-Heap (PriorityQueue) to track Time-To-Live (TTL) expirations efficiently.
 */
public class CacheShard {
    private final int capacity;
    private final Map<String, Node> cache;
    private final Node head;
    private final Node tail;

    // Min-Heap ensures O(1) retrieval of the nearest expiring node
    private final PriorityQueue<Node> ttlQueue;

    private final Lock lock = new ReentrantLock();

    /**
     * Constructs a new cache shard with a fixed capacity.
     *
     * @param capacity The maximum number of key-value pairs this shard can hold.
     */
    public CacheShard(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.ttlQueue = new PriorityQueue<>((a, b) -> Long.compare(a.expireAt, b.expireAt));
        this.head = new Node("HEAD", "DUMMY");
        this.tail = new Node("TAIL", "DUMMY");
        head.next = tail;
        tail.prev = head;
    }

    /**
     * Retrieves the value associated with the specified key.
     * Automatically handles lazy expiration and updates the LRU status.
     *
     * @param key The key to look up.
     * @return The associated value, or null if the key does not exist or has expired.
     */
    public String get(String key) {
        lock.lock();
        try {
            Node node = cache.get(key);
            if (node == null) return null;

            if (node.expireAt > 0 && System.currentTimeMillis() > node.expireAt) {
                removeNode(node);
                cache.remove(key);
                ttlQueue.remove(node);
                return null;
            }

            moveToHead(node);
            return node.value;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts or updates a key-value pair in the shard.
     * Evicts the least recently used element if the shard exceeds its capacity.
     *
     * @param key      The key to insert or update.
     * @param value    The value to store.
     * @param expireAt The absolute epoch timestamp in milliseconds when the key should expire (0 for no expiration).
     * @return The key that was evicted to free up space, or null if no eviction occurred.
     */
    public String set(String key, String value, long expireAt) {
        lock.lock();
        try {
            if (cache.containsKey(key)) {
                Node node = cache.get(key);
                if (node.expireAt > 0) {
                    ttlQueue.remove(node);
                }

                node.value = value;
                node.expireAt = expireAt;

                if (expireAt > 0) {
                    ttlQueue.add(node);
                }

                moveToHead(node);
                return null;
            } else {
                Node newNode = new Node(key, value);
                newNode.expireAt = expireAt;
                cache.put(key, newNode);
                addNodeAfterHead(newNode);

                if (expireAt > 0) {
                    ttlQueue.add(newNode);
                }

                if (cache.size() > capacity) {
                    Node lruNode = popTail();
                    cache.remove(lruNode.key);
                    if (lruNode.expireAt > 0) {
                        ttlQueue.remove(lruNode);
                    }
                    return lruNode.key;
                }
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a key-value pair from the shard.
     *
     * @param key The key to delete.
     * @return True if the key was found and deleted, false otherwise.
     */
    public boolean delete(String key) {
        lock.lock();
        try {
            Node node = cache.remove(key);
            if (node != null) {
                removeNode(node);
                if (node.expireAt > 0) {
                    ttlQueue.remove(node);
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if a key exists and is not expired without updating its LRU status.
     *
     * @param key The key to verify.
     * @return True if the key exists and is valid, false otherwise.
     */
    public boolean exists(String key) {
        lock.lock();
        try {
            Node node = cache.get(key);
            if (node == null) return false;
            return node.expireAt == 0 || System.currentTimeMillis() <= node.expireAt;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically increments the numerical value associated with the specified key by 1.
     * If the key does not exist or has expired, it is initialized to 1.
     *
     * @param key The key to increment.
     * @return The new incremented value.
     * @throws NumberFormatException if the existing value cannot be parsed as a Long.
     */
    public Long incr(String key) {
        lock.lock();
        try {
            Node node = cache.get(key);
            if (node == null || (node.expireAt > 0 && System.currentTimeMillis() > node.expireAt)) {
                if (node != null) {
                    removeNode(node);
                    cache.remove(key);
                    ttlQueue.remove(node);
                }
                set(key, "1", 0);
                return 1L;
            }
            long val = Long.parseLong(node.value);
            val++;
            node.value = String.valueOf(val);
            moveToHead(node);
            return val;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Calculates the remaining time to live for a specific key.
     *
     * @param key The key to evaluate.
     * @return -1 if the key has no expiration, -2 if the key does not exist (or expired),
     *         otherwise the remaining time in seconds.
     */
    public long ttl(String key) {
        lock.lock();
        try {
            Node node = cache.get(key);
            if (node == null) return -2;
            if (node.expireAt == 0) return -1;

            long remain = node.expireAt - System.currentTimeMillis();
            if (remain <= 0) {
                removeNode(node);
                cache.remove(key);
                ttlQueue.remove(node);
                return -2;
            }
            return remain / 1000;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves all keys currently stored in this shard.
     *
     * @return A list of all keys.
     */
    public List<String> getAllKeys() {
        lock.lock();
        try {
            return new ArrayList<>(cache.keySet());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Scans the Min-Heap to aggressively remove all expired keys.
     * Operates efficiently by stopping as soon as the peak element is still valid.
     *
     * @param now The current epoch timestamp in milliseconds.
     * @return A list of keys that were forcibly evicted during this cleanup cycle.
     */
    public List<String> cleanExpiredKeys(long now) {
        lock.lock();
        try {
            List<String> evicted = new ArrayList<>();
            while (!ttlQueue.isEmpty() && ttlQueue.peek().expireAt <= now) {
                Node expired = ttlQueue.poll();
                if (cache.containsKey(expired.key)) {
                    removeNode(expired);
                    cache.remove(expired.key);
                    evicted.add(expired.key);
                }
            }
            return evicted;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Completely wipes the shard data, clearing the map, queues, and resetting the LRU bounds.
     */
    public void clear() {
        lock.lock();
        try {
            cache.clear();
            ttlQueue.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts a node immediately after the dummy head, marking it as the most recently used.
     *
     * @param node The node to insert.
     */
    private void addNodeAfterHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    /**
     * Detaches a node from its current position in the doubly linked list.
     *
     * @param node The node to remove.
     */
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    /**
     * Moves an existing node to the front of the list, refreshing its LRU status.
     *
     * @param node The node to refresh.
     */
    private void moveToHead(Node node) {
        removeNode(node);
        addNodeAfterHead(node);
    }

    /**
     * Removes and returns the node positioned right before the dummy tail (least recently used).
     *
     * @return The evicted LRU node.
     */
    private Node popTail() {
        Node realTail = tail.prev;
        removeNode(realTail);
        return realTail;
    }
}