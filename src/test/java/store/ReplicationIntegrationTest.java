package store;

import network.DatabaseServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test validating the Master-Replica synchronization
 * and real-time command broadcasting over the TCP network layer.
 */
public class ReplicationIntegrationTest {

    private static final int MASTER_PORT = 9005;
    private KeyValueStore masterStore;
    private DatabaseServer masterServer;

    /**
     * Initializes the master store with pre-existing data to test initial snapshot transfers,
     * and starts the master TCP server on a background thread.
     *
     * @throws InterruptedException If the thread is interrupted while waiting for the port to bind.
     */
    @BeforeEach
    public void setUp() throws InterruptedException {
        masterStore = new ShardedCacheStore(1000, 16);

        masterStore.set("hero:1", "Batman");
        masterStore.setEx("hero:2", "Superman", 3600);

        masterServer = new DatabaseServer(masterStore, MASTER_PORT);
        Thread serverThread = new Thread(masterServer::start);
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(500);
    }

    /**
     * Simulates a replica node connecting to the master.
     * Verifies that the SYNC command triggers a complete state snapshot transfer,
     * and ensures that subsequent server mutations are broadcasted in real-time.
     *
     * @throws Exception If an unexpected network or I/O error occurs during the test.
     */
    @Test
    public void testReplicaSyncAndRealtimeBroadcast() throws Exception {
        try (Socket replicaSocket = new Socket("localhost", MASTER_PORT);
             DataOutputStream out = new DataOutputStream(replicaSocket.getOutputStream());
             DataInputStream in = new DataInputStream(replicaSocket.getInputStream())) {

            assertEquals("Connected to database!", in.readUTF());

            out.writeInt(1);
            out.writeUTF("SYNC");
            out.flush();

            boolean receivedBatman = false;
            boolean receivedSuperman = false;

            for (int i = 0; i < 2; i++) {
                int numArgs = in.readInt();
                String[] parts = new String[numArgs];

                for (int j = 0; j < numArgs; j++) {
                    parts[j] = in.readUTF();
                }

                if (parts[0].equals("SET") && parts[1].equals("hero:1") && parts[2].equals("Batman")) {
                    receivedBatman = true;
                }
                if (parts[0].equals("SET_EX") && parts[1].equals("hero:2") && parts[2].equals("Superman")) {
                    receivedSuperman = true;
                }
            }

            assertTrue(receivedBatman, "Replica did not receive Batman in the initial snapshot.");
            assertTrue(receivedSuperman, "Replica did not receive Superman in the initial snapshot.");

            masterStore.set("hero:3", "Flash");
            masterServer.broadcastToReplicas(new String[]{"SET", "hero:3", "Flash"});

            int numArgs = in.readInt();
            assertEquals(3, numArgs);
            assertEquals("SET", in.readUTF());
            assertEquals("hero:3", in.readUTF());
            assertEquals("Flash", in.readUTF());
        }
    }
}