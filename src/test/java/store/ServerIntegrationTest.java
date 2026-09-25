package store;

import network.DatabaseServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the TCP network layer.
 * Validates the custom length-prefixed binary protocol parser and ensures
 * that client commands are correctly deserialized and executed by the server.
 */
public class ServerIntegrationTest {

    private static final int TEST_PORT = 9999;

    /**
     * Initializes the storage engine and starts the database server on a background daemon thread
     * before running the test suite.
     *
     * @throws InterruptedException If the main thread is interrupted while waiting for the port to bind.
     */
    @BeforeAll
    public static void startTestServer() throws InterruptedException {
        KeyValueStore store = new ShardedCacheStore(100, 16);
        DatabaseServer server = new DatabaseServer(store, TEST_PORT);

        Thread serverThread = new Thread(server::start);
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(500);
    }

    /**
     * Simulates a raw socket client connecting to the server and executing operations
     * using the custom binary protocol. Verifies that complex payloads (like JSON strings)
     * are safely transmitted and retrieved without corruption.
     *
     * @throws Exception If an unexpected network or I/O error occurs.
     */
    @Test
    public void testTcpBinaryProtocol() throws Exception {
        try (Socket socket = new Socket("localhost", TEST_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            String welcomeMessage = in.readUTF();
            assertEquals("Connected to database!", welcomeMessage);

            out.writeInt(3);
            out.writeUTF("SET");
            out.writeUTF("config");
            out.writeUTF("{\"theme\": \"dark\", \"version\": 2.0}");
            out.flush();

            assertEquals("OK", in.readUTF());

            out.writeInt(2);
            out.writeUTF("GET");
            out.writeUTF("config");
            out.flush();

            assertEquals("{\"theme\": \"dark\", \"version\": 2.0}", in.readUTF());
        }
    }
}