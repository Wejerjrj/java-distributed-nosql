package network;

import store.KeyValueStore;
import store.LruCacheStore;
import store.WalDecorator;

/**
 * Entry point for the Master database node.
 * Initializes the storage engine, replays the Write-Ahead Log (WAL) for crash recovery,
 * and starts the TCP server to accept client connections and synchronize replicas.
 */
public class MasterNode {

    public static void main(String[] args) {
        System.out.println("=== STARTING MASTER NODE ===");

        KeyValueStore baseStore = new LruCacheStore(100);
        WalDecorator masterStore = new WalDecorator(baseStore, "master.log");

        masterStore.replayLog();

        DatabaseServer server = new DatabaseServer(masterStore, 9000);
        server.start();
    }
}