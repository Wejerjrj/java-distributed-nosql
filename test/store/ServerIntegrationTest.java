package store;

import network.DatabaseServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end TCP integration tests validating the server protocol and command parser.
 */
public class ServerIntegrationTest {

    private static final int TEST_PORT = 9999;

    @BeforeAll
    public static void startTestServer() throws InterruptedException {
        KeyValueStore store = new LruCacheStore(100);
        DatabaseServer server = new DatabaseServer(store, TEST_PORT);

        Thread serverThread = new Thread(server::start);
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(500);
    }

    @Test
    public void testTcpClientCommunicationAndParsing() throws Exception {
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String welcomeMessage = in.readLine();
            assertEquals("Connected to database!", welcomeMessage);

            out.println("SET config {\"theme\": \"dark\", \"version\": 2.0}");
            assertEquals("OK", in.readLine());

            out.println("GET config");
            assertEquals("{\"theme\": \"dark\", \"version\": 2.0}", in.readLine());

            out.println("EXISTS config");
            assertEquals("(integer) 1", in.readLine());

            out.println("INCR non_existing_counter");
            assertEquals("(integer) 1", in.readLine());

            out.println("UNKNOWN_COMMAND");
            assertEquals("Error: Unknown command", in.readLine());
        }
    }
}