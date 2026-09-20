package network;

import store.KeyValueStore;
import store.LruCacheStore;
import store.WalDecorator;

import java.io.*;
import java.net.Socket;

/**
 * Entry point for the Replica database node.
 * Initializes a local storage engine, connects to the Master node to receive real-time
 * replication updates, and starts a server on port 9001 to handle read-heavy client workloads.
 */
public class ReplicaNode {

    public static void main(String[] args) {
        System.out.println("=== STARTING REPLICA NODE ===");

        KeyValueStore baseStore = new LruCacheStore(100);
        WalDecorator replicaStore = new WalDecorator(baseStore, "replica.log");
        replicaStore.replayLog();

        Thread syncThread = new Thread(() -> {
            try (
                    Socket masterSocket = new Socket("localhost", 9000);
                    PrintWriter outToMaster = new PrintWriter(masterSocket.getOutputStream(), true);
                    BufferedReader inFromMaster = new BufferedReader(new InputStreamReader(masterSocket.getInputStream()))
            ) {
                inFromMaster.readLine();
                outToMaster.println("SYNC");
                System.out.println("[SYNC] Connected to Master. Awaiting data stream...");

                String commandLine;
                while ((commandLine = inFromMaster.readLine()) != null) {
                    System.out.println("[SYNC] Received from Master: " + commandLine);
                    String[] parts = commandLine.split("\\s+");
                    if (parts.length == 0) continue;

                    String command = parts[0].toUpperCase();

                    if (command.equals("SET") && parts.length >= 3) {
                        String[] split3 = commandLine.split("\\s+", 3);
                        replicaStore.set(split3[1], split3[2]);
                    } else if (command.equals("SET_EX") && parts.length >= 4) {
                        String secondsStr = parts[parts.length - 1];
                        int seconds = Integer.parseInt(secondsStr);
                        String[] split3 = commandLine.split("\\s+", 3);
                        String remainder = split3[2];
                        String value = remainder.substring(0, remainder.lastIndexOf(secondsStr)).trim();
                        replicaStore.setEx(parts[1], value, seconds);
                    } else if (command.equals("DELETE") && parts.length == 2) {
                        replicaStore.delete(parts[1]);
                    } else if (command.equals("INCR") && parts.length == 2) {
                        replicaStore.incr(parts[1]);
                    } else if (command.equals("FLUSHALL")) {
                        replicaStore.flushAll();
                    }
                }
            } catch (Exception e) {
                System.err.println("[SYNC] Master unreachable or disconnected.");
            }
        });
        syncThread.start();

        DatabaseServer server = new DatabaseServer(replicaStore, 9001);
        server.start();
    }
}