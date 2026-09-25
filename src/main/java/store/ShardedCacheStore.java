package store;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A distributed in-memory storage engine implementing Lock Striping for high concurrency.
 * The database is divided into multiple independent shards to eliminate global write bottlenecks,
 * while secondary indexes (Trie and AVL Tree) are protected by a global ReadWriteLock to ensure structural integrity.
 */
public class ShardedCacheStore implements KeyValueStore {

    private final CacheShard[] shards;
    private final int numShards;

    private final Trie trie;
    private final AVLTree avlTree;
    private final ReadWriteLock indexLock = new ReentrantReadWriteLock();

    /**
     * Constructs a new ShardedCacheStore and initializes the background cleanup thread.
     *
     * @param totalCapacity The maximum number of elements the entire store can hold.
     * @param numShards     The number of independent memory shards to create.
     */
    public ShardedCacheStore(int totalCapacity, int numShards) {
        this.numShards = numShards;
        this.shards = new CacheShard[numShards];

        int capacityPerShard = Math.max(1, totalCapacity / numShards);

        for (int i = 0; i < numShards; i++) {
            this.shards[i] = new CacheShard(capacityPerShard);
        }

        this.trie = new Trie();
        this.avlTree = new AVLTree();

        startEvictionThread();
    }

    /**
     * Determines which shard is responsible for a given key based on its hash.
     *
     * @param key The key to route.
     * @return The corresponding CacheShard instance.
     */
    private CacheShard getShard(String key) {
        return shards[Math.abs(key.hashCode() % numShards)];
    }

    @Override
    public String get(String key) {
        return getShard(key).get(key);
    }

    @Override
    public void set(String key, String value) {
        CacheShard shard = getShard(key);
        String evictedKey = shard.set(key, value, 0);
        updateIndexesOnWrite(key, evictedKey);
    }

    @Override
    public void setEx(String key, String value, int seconds) {
        CacheShard shard = getShard(key);
        long expireAt = System.currentTimeMillis() + (seconds * 1000L);
        String evictedKey = shard.set(key, value, expireAt);
        updateIndexesOnWrite(key, evictedKey);
    }

    /**
     * Safely updates the secondary indexes after a write operation.
     *
     * @param newKey     The newly inserted or updated key.
     * @param evictedKey The key evicted by the LRU capacity policy, or null if no eviction occurred.
     */
    private void updateIndexesOnWrite(String newKey, String evictedKey) {
        indexLock.writeLock().lock();
        try {
            trie.insert(newKey);
            avlTree.insert(newKey);

            if (evictedKey != null) {
                trie.remove(evictedKey);
                avlTree.delete(evictedKey);
            }
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public boolean delete(String key) {
        boolean deleted = getShard(key).delete(key);

        if (deleted) {
            indexLock.writeLock().lock();
            try {
                trie.remove(key);
                avlTree.delete(key);
            } finally {
                indexLock.writeLock().unlock();
            }
        }
        return deleted;
    }

    @Override
    public boolean exists(String key) {
        return getShard(key).exists(key);
    }

    @Override
    public Long incr(String key) {
        CacheShard shard = getShard(key);
        boolean existsBefore = shard.exists(key);
        Long val = shard.incr(key);

        if (!existsBefore) {
            indexLock.writeLock().lock();
            try {
                trie.insert(key);
                avlTree.insert(key);
            } finally {
                indexLock.writeLock().unlock();
            }
        }
        return val;
    }

    @Override
    public long ttl(String key) {
        return getShard(key).ttl(key);
    }

    @Override
    public void flushAll() {
        for (CacheShard shard : shards) {
            shard.clear();
        }

        indexLock.writeLock().lock();
        try {
            trie.clear();
            avlTree.clear();
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public List<String> getAllKeys() {
        List<String> allKeys = new ArrayList<>();
        for (CacheShard shard : shards) {
            allKeys.addAll(shard.getAllKeys());
        }
        return allKeys;
    }

    @Override
    public List<String> prefixMatch(String prefix, int limit) {
        indexLock.readLock().lock();
        try {
            return trie.getKeysWithPrefix(prefix, limit);
        } finally {
            indexLock.readLock().unlock();
        }
    }

    @Override
    public List<String> range(String start, String end, int limit) {
        indexLock.readLock().lock();
        try {
            return avlTree.range(start, end, limit);
        } finally {
            indexLock.readLock().unlock();
        }
    }

    /**
     * Spawns a background daemon thread that periodically purges expired TTL keys.
     * The cleanup iterates over one shard at a time rather than freezing the entire system,
     * preventing catastrophic O(N) global GC-style pauses.
     */
    private void startEvictionThread() {
        Thread cleaner = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    long now = System.currentTimeMillis();

                    for (CacheShard shard : shards) {
                        List<String> evicted = shard.cleanExpiredKeys(now);

                        if (!evicted.isEmpty()) {
                            indexLock.writeLock().lock();
                            try {
                                for (String key : evicted) {
                                    trie.remove(key);
                                    avlTree.delete(key);
                                }
                            } finally {
                                indexLock.writeLock().unlock();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleaner.setDaemon(true);
        cleaner.start();
    }
}