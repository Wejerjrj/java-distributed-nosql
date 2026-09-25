package network;

import store.KeyValueStore;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP server that accepts client connections and routes commands to the underlying KeyValueStore.
 * Handles replication by broadcasting write operations to connected replica nodes.
 */
public class DatabaseServer {
    private final KeyValueStore store;
    private final int port;
    private final List<DataOutputStream> replicas = new CopyOnWriteArrayList<>();
    private final ExecutorService threadPool = Executors.newFixedThreadPool(100);

    /**
     * Constructs a new DatabaseServer.
     *
     * @param store The underlying KeyValueStore engine.
     * @param port  The port on which the server will listen for incoming connections.
     */
    public DatabaseServer(KeyValueStore store, int port) {
        this.store = store;
        this.port = port;
    }

    /**
     * Starts the server loop to accept incoming client connections.
     * Each connection is delegated to a dedicated worker thread from the pool.
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Database server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(new ClientHandler(clientSocket, store, this));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Broadcasts a write command to all connected replica nodes.
     * Automatically removes replicas that are no longer reachable.
     *
     * @param commandParts The parsed command arguments to broadcast.
     */
    public void broadcastToReplicas(String[] commandParts) {
        for (DataOutputStream replicaOut : replicas) {
            try {
                synchronized (replicaOut) {
                    replicaOut.writeInt(commandParts.length);
                    for (String part : commandParts) {
                        replicaOut.writeUTF(part);
                    }
                    replicaOut.flush();
                }
            } catch (IOException e) {
                replicas.remove(replicaOut);
                System.err.println("[Replication] Replica disconnected and removed cleanly.");
            }
        }
    }

    /**
     * Runnable task responsible for handling an individual client or replica connection.
     * Parses the custom binary protocol and executes commands against the store.
     */
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final KeyValueStore store;
        private final DatabaseServer server;

        /**
         * Constructs a new ClientHandler.
         *
         * @param socket The client connection socket.
         * @param store  The underlying KeyValueStore.
         * @param server The parent server instance for managing replication.
         */
        public ClientHandler(Socket socket, KeyValueStore store, DatabaseServer server) {
            this.socket = socket;
            this.store = store;
            this.server = server;
        }

        @Override
        public void run() {
            try (
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream())
            ) {
                out.writeUTF("Connected to database!");

                while (true) {
                    int numArgs = in.readInt();
                    String[] parts = new String[numArgs];

                    for (int i = 0; i < numArgs; i++) {
                        parts[i] = in.readUTF();
                    }

                    if (parts.length == 0) continue;
                    String command = parts[0].toUpperCase();

                    switch (command) {
                        case "SYNC":
                            server.replicas.add(out);
                            System.out.println("[Replication] New Replica connected. Starting state transfer...");
                            List<String> keys = store.getAllKeys();
                            synchronized (out) {
                                for (String k : keys) {
                                    String v = store.get(k);
                                    if (v == null) continue;

                                    long ttl = store.ttl(k);
                                    if (ttl > 0) {
                                        out.writeInt(4);
                                        out.writeUTF("SET_EX");
                                        out.writeUTF(k);
                                        out.writeUTF(v);
                                        out.writeUTF(String.valueOf(ttl));
                                    } else {
                                        out.writeInt(3);
                                        out.writeUTF("SET");
                                        out.writeUTF(k);
                                        out.writeUTF(v);
                                    }
                                }
                                out.flush();
                            }
                            System.out.println("[Replication] Snapshot transfer complete.");
                            break;
                        case "SET":
                            if (parts.length == 3) {
                                store.set(parts[1], parts[2]);
                                out.writeUTF("OK");
                                server.broadcastToReplicas(parts);
                            } else out.writeUTF("Error: SET <key> <value>");
                            break;
                        case "SET_EX":
                            if (parts.length == 4) {
                                try {
                                    int seconds = Integer.parseInt(parts[3]);
                                    store.setEx(parts[1], parts[2], seconds);
                                    out.writeUTF("OK");
                                    server.broadcastToReplicas(parts);
                                } catch (NumberFormatException e) {
                                    out.writeUTF("Error: Invalid time format");
                                }
                            } else out.writeUTF("Error: SET_EX <key> <value> <seconds>");
                            break;
                        case "DELETE":
                            if (parts.length == 2) {
                                boolean deleted = store.delete(parts[1]);
                                out.writeUTF(deleted ? "OK" : "(nil)");
                                if (deleted) server.broadcastToReplicas(parts);
                            } else out.writeUTF("Error: DELETE <key>");
                            break;
                        case "EXISTS":
                            if (parts.length == 2) out.writeUTF(store.exists(parts[1]) ? "(integer) 1" : "(integer) 0");
                            else out.writeUTF("Error: EXISTS <key>");
                            break;
                        case "INCR":
                            if (parts.length == 2) {
                                try {
                                    Long val = store.incr(parts[1]);
                                    out.writeUTF("(integer) " + val);
                                    server.broadcastToReplicas(parts);
                                } catch (NumberFormatException e) {
                                    out.writeUTF("Error: Value is not an integer");
                                }
                            } else out.writeUTF("Error: INCR <key>");
                            break;
                        case "TTL":
                            if (parts.length == 2) out.writeUTF("(integer) " + store.ttl(parts[1]));
                            else out.writeUTF("Error: TTL <key>");
                            break;
                        case "FLUSHALL":
                            store.flushAll();
                            out.writeUTF("OK");
                            server.broadcastToReplicas(parts);
                            break;
                        case "GET":
                            if (parts.length == 2) {
                                String value = store.get(parts[1]);
                                out.writeUTF(value != null ? value : "(nil)");
                            } else out.writeUTF("Error: GET <key>");
                            break;
                        case "PREFIX":
                            if (parts.length == 2) {
                                List<String> matches = store.prefixMatch(parts[1], 100);
                                out.writeUTF(matches.isEmpty() ? "(empty list)" : String.join(", ", matches));
                            } else out.writeUTF("Error: PREFIX <string>");
                            break;
                        case "RANGE":
                            if (parts.length == 3) {
                                List<String> rangeMatches = store.range(parts[1], parts[2], 100);
                                out.writeUTF(rangeMatches.isEmpty() ? "(empty list)" : String.join(", ", rangeMatches));
                            } else out.writeUTF("Error: RANGE <start> <end>");
                            break;
                        case "EXIT":
                        case "QUIT":
                            out.writeUTF("Disconnected.");
                            socket.close();
                            return;
                        default:
                            out.writeUTF("Error: Unknown command");
                    }
                }
            } catch (EOFException e) {
                // Clean client disconnect
            } catch (IOException e) {
                // Unexpected network drop
            }
        }
    }
}