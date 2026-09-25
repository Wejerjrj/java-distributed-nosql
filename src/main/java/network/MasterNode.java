package network;

import store.KeyValueStore;
import store.ShardedCacheStore;
import store.WalDecorator;

/**
 * Entry point for the master database node.
 * Initializes the storage engine, replays the Write-Ahead Log (WAL) for crash recovery,
 * and starts the TCP server to accept client connections and synchronize replicas.
 */
public class MasterNode {

    /**
     * Initializes the in-memory sharded cache, wraps it with the WAL decorator,
     * triggers the log replay mechanism, and binds the server to a specific port.
     *
     * @param args Command line arguments (unused).
     */
    public static void main(String[] args) {
        System.out.println("=== STARTING MASTER NODE ===");

        KeyValueStore baseStore = new ShardedCacheStore(100000, 16);
        WalDecorator masterStore = new WalDecorator(baseStore, "master.log");

        masterStore.replayLog();

        DatabaseServer server = new DatabaseServer(masterStore, 9000);
        server.start();
    }
}