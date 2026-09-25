package network;

import store.KeyValueStore;
import store.ShardedCacheStore;

import java.io.*;
import java.net.Socket;

/**
 * Entry point for a replica database node.
 * A replica operates purely in-memory to maximize read throughput and does not utilize a Write-Ahead Log.
 * It maintains a persistent TCP connection to the master node, requests a full state synchronization
 * upon connection, and continuously streams real-time state changes.
 */
public class ReplicaNode {

    /**
     * Initializes the in-memory store and spawns a background synchronization thread
     * to fetch and apply updates from the master node. Subsequently binds a local server
     * on a distinct port to accept incoming client read requests.
     *
     * @param args Command line arguments (unused).
     */
    public static void main(String[] args) {
        System.out.println("=== STARTING REPLICA NODE ===");

        KeyValueStore replicaStore = new ShardedCacheStore(100000, 16);

        Thread syncThread = new Thread(() -> {
            while (true) {
                try (
                        Socket masterSocket = new Socket("localhost", 9000);
                        DataOutputStream outToMaster = new DataOutputStream(masterSocket.getOutputStream());
                        DataInputStream inFromMaster = new DataInputStream(masterSocket.getInputStream())
                ) {
                    System.out.println(inFromMaster.readUTF());

                    outToMaster.writeInt(1);
                    outToMaster.writeUTF("SYNC");
                    outToMaster.flush();

                    System.out.println("[SYNC] Connected to master. Awaiting data stream...");

                    while (true) {
                        int numArgs = inFromMaster.readInt();
                        String[] parts = new String[numArgs];

                        for (int i = 0; i < numArgs; i++) {
                            parts[i] = inFromMaster.readUTF();
                        }

                        if (parts.length == 0) {
                            continue;
                        }

                        String command = parts[0].toUpperCase();

                        if (command.equals("SET") && parts.length == 3) {
                            replicaStore.set(parts[1], parts[2]);
                        } else if (command.equals("SET_EX") && parts.length == 4) {
                            replicaStore.setEx(parts[1], parts[2], Integer.parseInt(parts[3]));
                        } else if (command.equals("DELETE") && parts.length == 2) {
                            replicaStore.delete(parts[1]);
                        } else if (command.equals("INCR") && parts.length == 2) {
                            replicaStore.incr(parts[1]);
                        } else if (command.equals("FLUSHALL")) {
                            replicaStore.flushAll();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[SYNC] Master connection lost. Retrying in 3 seconds...");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });

        syncThread.start();

        DatabaseServer server = new DatabaseServer(replicaStore, 9001);
        server.start();
    }
}