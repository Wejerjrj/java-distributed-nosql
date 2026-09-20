package store;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A high-performance, thread-safe Key-Value store with LRU eviction and secondary indexing.
 * Utilizes ReentrantReadWriteLock to allow highly concurrent read operations.
 */
public class LruCacheStore implements KeyValueStore {

    private final int capacity;
    private final Map<String, Node> cache;
    private final Node head;
    private final Node tail;
    private final Trie trie;
    private final AVLTree avlTree;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    /**
     * Constructs a new LruCacheStore.
     *
     * @param capacity The maximum number of entries before LRU eviction occurs.
     */
    public LruCacheStore(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.trie = new Trie();
        this.avlTree = new AVLTree();

        this.head = new Node("HEAD", "DUMMY");
        this.tail = new Node("TAIL", "DUMMY");
        head.next = tail;
        tail.prev = head;

        Thread cleaner = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    cleanExpiredKeys();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        cleaner.setDaemon(true);
        cleaner.start();
    }

    @Override
    public String get(String key) {
        writeLock.lock();
        try {
            if (!cache.containsKey(key)) {
                return null;
            }
            Node node = cache.get(key);

            if (node.expireAt > 0 && System.currentTimeMillis() > node.expireAt) {
                delete(key);
                return null;
            }

            moveToHead(node);
            return node.value;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void set(String key, String value) {
        writeLock.lock();
        try {
            if (cache.containsKey(key)) {
                Node node = cache.get(key);
                node.value = value;
                node.expireAt = 0;
                moveToHead(node);
            } else {
                Node newNode = new Node(key, value);
                cache.put(key, newNode);
                trie.insert(key);
                avlTree.insert(key);
                addNodeAfterHead(newNode);

                if (cache.size() > capacity) {
                    Node lruNode = popTail();
                    cache.remove(lruNode.key);
                    trie.remove(lruNode.key);
                    avlTree.delete(lruNode.key);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void setEx(String key, String value, int seconds) {
        writeLock.lock();
        try {
            set(key, value);
            if (cache.containsKey(key)) {
                cache.get(key).expireAt = System.currentTimeMillis() + (seconds * 1000L);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean delete(String key) {
        writeLock.lock();
        try {
            if (cache.containsKey(key)) {
                Node node = cache.get(key);
                removeNode(node);
                cache.remove(key);
                trie.remove(key);
                avlTree.delete(key);
                return true;
            }
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean exists(String key) {
        readLock.lock();
        try {
            Node node = cache.get(key);
            if (node == null) {
                return false;
            }
            if (node.expireAt > 0 && System.currentTimeMillis() > node.expireAt) {
                return false;
            }
            return true;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Long incr(String key) {
        writeLock.lock();
        try {
            Node node = cache.get(key);
            if (node == null || (node.expireAt > 0 && System.currentTimeMillis() > node.expireAt)) {
                if (node != null) {
                    delete(key);
                }
                set(key, "1");
                return 1L;
            }

            long val = Long.parseLong(node.value);
            val++;
            node.value = String.valueOf(val);
            moveToHead(node);
            return val;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long ttl(String key) {
        readLock.lock();
        try {
            Node node = cache.get(key);
            if (node == null) {
                return -2;
            }
            if (node.expireAt == 0) {
                return -1;
            }
            long remain = node.expireAt - System.currentTimeMillis();
            if (remain <= 0) {
                return -2;
            }
            return remain / 1000;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void flushAll() {
        writeLock.lock();
        try {
            cache.clear();
            trie.clear();
            avlTree.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public List<String> getAllKeys() {
        readLock.lock();
        try {
            return new ArrayList<>(cache.keySet());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<String> prefixMatch(String prefix) {
        readLock.lock();
        try {
            return trie.getKeysWithPrefix(prefix);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<String> range(String start, String end) {
        readLock.lock();
        try {
            return avlTree.range(start, end);
        } finally {
            readLock.unlock();
        }
    }

    private void cleanExpiredKeys() {
        writeLock.lock();
        try {
            long now = System.currentTimeMillis();
            List<String> keysToEvict = new ArrayList<>();

            for (Node node : cache.values()) {
                if (node.expireAt > 0 && now > node.expireAt) {
                    keysToEvict.add(node.key);
                }
            }

            for (String key : keysToEvict) {
                delete(key);
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void addNodeAfterHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node node) {
        Node previous = node.prev;
        Node nextNode = node.next;
        previous.next = nextNode;
        nextNode.prev = previous;
    }

    private void moveToHead(Node node) {
        removeNode(node);
        addNodeAfterHead(node);
    }

    private Node popTail() {
        Node realTail = tail.prev;
        removeNode(realTail);
        return realTail;
    }
}