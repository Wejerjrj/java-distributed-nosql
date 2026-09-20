package network;

import store.KeyValueStore;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A multithreaded TCP server that handles client connections, advanced command parsing,
 * and manages data replication for distributed architecture.
 */
public class DatabaseServer {

    private final KeyValueStore store;
    private final int port;
    private final List<PrintWriter> replicas = new CopyOnWriteArrayList<>();

    /**
     * Constructs a new network.DatabaseServer.
     *
     * @param store The underlying KeyValueStore implementation to use.
     * @param port  The TCP port on which the server will listen.
     */
    public DatabaseServer(KeyValueStore store, int port) {
        this.store = store;
        this.port = port;
    }

    /**
     * Starts the server loop to accept incoming client and replica connections.
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Database server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket, store, this)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Broadcasts a state-changing command to all connected replicas.
     *
     * @param command The raw command string to propagate.
     */
    public void broadcastToReplicas(String command) {
        for (PrintWriter replicaOut : replicas) {
            replicaOut.println(command);
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final KeyValueStore store;
        private final DatabaseServer server;

        public ClientHandler(Socket socket, KeyValueStore store, DatabaseServer server) {
            this.socket = socket;
            this.store = store;
            this.server = server;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                out.println("Connected to database!");
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    inputLine = inputLine.trim();
                    if (inputLine.isEmpty()) continue;

                    String[] parts = inputLine.split("\\s+");
                    String command = parts[0].toUpperCase();

                    switch (command) {
                        case "SYNC":
                            server.replicas.add(out);
                            System.out.println("[Replication] New Replica connected and synchronized.");
                            break;

                        case "SET":
                            String[] setParts = inputLine.split("\\s+", 3);
                            if (setParts.length == 3) {
                                store.set(setParts[1], setParts[2]);
                                out.println("OK");
                                server.broadcastToReplicas(inputLine);
                            } else {
                                out.println("Error: SET <key> <value>");
                            }
                            break;

                        case "SET_EX":
                            if (parts.length >= 4) {
                                try {
                                    String secondsStr = parts[parts.length - 1];
                                    int seconds = Integer.parseInt(secondsStr);

                                    String[] split3 = inputLine.split("\\s+", 3);
                                    String remainder = split3[2];
                                    String value = remainder.substring(0, remainder.lastIndexOf(secondsStr)).trim();

                                    store.setEx(parts[1], value, seconds);
                                    out.println("OK");
                                    server.broadcastToReplicas(inputLine);
                                } catch (NumberFormatException e) {
                                    out.println("Error: Invalid time format");
                                }
                            } else {
                                out.println("Error: SET_EX <key> <value> <seconds>");
                            }
                            break;

                        case "DELETE":
                            if (parts.length == 2) {
                                boolean deleted = store.delete(parts[1]);
                                out.println(deleted ? "OK" : "(nil)");
                                if (deleted) {
                                    server.broadcastToReplicas(inputLine);
                                }
                            } else {
                                out.println("Error: DELETE <key>");
                            }
                            break;

                        case "EXISTS":
                            if (parts.length == 2) {
                                out.println(store.exists(parts[1]) ? "(integer) 1" : "(integer) 0");
                            } else {
                                out.println("Error: EXISTS <key>");
                            }
                            break;

                        case "INCR":
                            if (parts.length == 2) {
                                try {
                                    Long val = store.incr(parts[1]);
                                    out.println("(integer) " + val);
                                    server.broadcastToReplicas(inputLine);
                                } catch (NumberFormatException e) {
                                    out.println("Error: Value is not an integer or out of range");
                                }
                            } else {
                                out.println("Error: INCR <key>");
                            }
                            break;

                        case "TTL":
                            if (parts.length == 2) {
                                out.println("(integer) " + store.ttl(parts[1]));
                            } else {
                                out.println("Error: TTL <key>");
                            }
                            break;

                        case "FLUSHALL":
                            store.flushAll();
                            out.println("OK");
                            server.broadcastToReplicas(inputLine);
                            break;

                        case "GET":
                            if (parts.length == 2) {
                                String value = store.get(parts[1]);
                                out.println(value != null ? value : "(nil)");
                            } else {
                                out.println("Error: GET <key>");
                            }
                            break;

                        case "PREFIX":
                            if (parts.length == 2) {
                                List<String> matches = store.prefixMatch(parts[1]);
                                out.println(matches.isEmpty() ? "(empty list)" : String.join(", ", matches));
                            } else {
                                out.println("Error: PREFIX <string>");
                            }
                            break;

                        case "RANGE":
                            if (parts.length == 3) {
                                List<String> rangeMatches = store.range(parts[1], parts[2]);
                                out.println(rangeMatches.isEmpty() ? "(empty list)" : String.join(", ", rangeMatches));
                            } else {
                                out.println("Error: RANGE <start> <end>");
                            }
                            break;

                        case "EXIT":
                        case "QUIT":
                            out.println("Disconnected.");
                            socket.close();
                            return;

                        default:
                            out.println("Error: Unknown command");
                    }
                }
            } catch (IOException e) {
                // Expected behavior when a client or replica disconnects abruptly
            }
        }
    }
}