package store;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A decorator for KeyValueStore that provides asynchronous Write-Ahead Logging (WAL)
 * for durability and crash recovery.
 * It uses a producer-consumer pattern to offload disk I/O to a background thread,
 * ensuring that client operations remain strictly in-memory and non-blocking.
 */
public class WalDecorator implements KeyValueStore, AutoCloseable {

    private static final int COMPACTION_THRESHOLD = 10000;

    private final KeyValueStore innerStore;
    private final String logFilePath;
    private DataOutputStream outStream;
    private final Lock fileLock = new ReentrantLock();

    private final BlockingQueue<String[]> writeQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger operationCount = new AtomicInteger(0);

    private volatile boolean isRunning = true;
    private volatile boolean isCompacting = false;
    private final Thread flusherThread;

    /**
     * Wraps an existing KeyValueStore with a WAL and initializes the background flusher thread.
     *
     * @param innerStore  The underlying in-memory storage engine.
     * @param logFilePath The path to the physical log file on disk.
     */
    public WalDecorator(KeyValueStore innerStore, String logFilePath) {
        this.innerStore = innerStore;
        this.logFilePath = logFilePath;

        try {
            this.outStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(logFilePath, true)));
        } catch (IOException e) {
            System.err.println("Fatal error: Cannot initialize WAL - " + e.getMessage());
        }

        this.flusherThread = new Thread(() -> {
            try {
                while (isRunning || !writeQueue.isEmpty()) {
                    String[] command = writeQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (command != null) {
                        fileLock.lock();
                        try {
                            // Binary encoding: array length prefix followed by UTF payloads
                            outStream.writeInt(command.length);
                            for (String arg : command) {
                                outStream.writeUTF(arg);
                            }

                            if (writeQueue.isEmpty()) {
                                outStream.flush();
                            }
                        } finally {
                            fileLock.unlock();
                        }

                        if (operationCount.incrementAndGet() >= COMPACTION_THRESHOLD && !isCompacting) {
                            compactAsync();
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("WAL flusher error: " + e.getMessage());
            }
        });

        this.flusherThread.setDaemon(true);
        this.flusherThread.start();
    }

    /**
     * Reads the binary log file from disk and replays all recorded operations
     * to reconstruct the exact in-memory state after a crash or restart.
     */
    public void replayLog() {
        File file = new File(logFilePath);
        if (!file.exists()) return;

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            while (true) {
                try {
                    int numArgs = in.readInt();
                    String[] parts = new String[numArgs];

                    for (int i = 0; i < numArgs; i++) {
                        parts[i] = in.readUTF();
                    }

                    if (parts.length == 0) continue;
                    String command = parts[0].toUpperCase();

                    if (command.equals("SET") && parts.length == 3) {
                        innerStore.set(parts[1], parts[2]);
                    } else if (command.equals("SET_EX") && parts.length == 4) {
                        innerStore.setEx(parts[1], parts[2], Integer.parseInt(parts[3]));
                    } else if (command.equals("DELETE") && parts.length == 2) {
                        innerStore.delete(parts[1]);
                    } else if (command.equals("INCR") && parts.length == 2) {
                        innerStore.incr(parts[1]);
                    } else if (command.equals("FLUSHALL")) {
                        innerStore.flushAll();
                    }
                } catch (EOFException e) {
                    break; // Clean EOF reached
                }
            }
        } catch (IOException e) {
            System.err.println("Error during WAL replay: " + e.getMessage());
        }
    }

    /**
     * Triggers an asynchronous compaction of the log file to prevent infinite disk growth.
     * Takes a snapshot of the current state and swaps it seamlessly with the active log.
     */
    private void compactAsync() {
        isCompacting = true;

        Thread compactionThread = new Thread(() -> {
            try {
                File tempFile = new File(logFilePath + ".tmp");

                try (DataOutputStream tempOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile, false)))) {
                    List<String> keys = innerStore.getAllKeys();

                    for (String key : keys) {
                        String value = innerStore.get(key);
                        if (value == null) continue;

                        long ttl = innerStore.ttl(key);
                        if (ttl > 0) {
                            tempOut.writeInt(4);
                            tempOut.writeUTF("SET_EX");
                            tempOut.writeUTF(key);
                            tempOut.writeUTF(value);
                            tempOut.writeUTF(String.valueOf(ttl));
                        } else {
                            tempOut.writeInt(3);
                            tempOut.writeUTF("SET");
                            tempOut.writeUTF(key);
                            tempOut.writeUTF(value);
                        }
                    }
                    tempOut.flush();
                }

                fileLock.lock();
                try {
                    outStream.close();
                    File originalFile = new File(logFilePath);
                    Files.move(tempFile.toPath(), originalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    outStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(logFilePath, true)));
                    operationCount.set(innerStore.getAllKeys().size());
                } finally {
                    fileLock.unlock();
                }
            } catch (IOException e) {
                System.err.println("Error during async WAL compaction: " + e.getMessage());
            } finally {
                isCompacting = false;
            }
        });

        compactionThread.start();
    }

    /**
     * Submits a parsed command to the blocking queue for background processing.
     *
     * @param command The variable arguments representing the command and its parameters.
     */
    private void queueWrite(String... command) {
        writeQueue.offer(command);
    }

    @Override
    public void set(String key, String value) {
        innerStore.set(key, value);
        queueWrite("SET", key, value);
    }

    @Override
    public void setEx(String key, String value, int seconds) {
        innerStore.setEx(key, value, seconds);
        queueWrite("SET_EX", key, value, String.valueOf(seconds));
    }

    @Override
    public boolean delete(String key) {
        boolean deleted = innerStore.delete(key);
        if (deleted) queueWrite("DELETE", key);
        return deleted;
    }

    @Override
    public Long incr(String key) {
        Long val = innerStore.incr(key);
        queueWrite("INCR", key);
        return val;
    }

    @Override
    public void flushAll() {
        innerStore.flushAll();
        queueWrite("FLUSHALL");
    }

    // Read operations strictly bypass the WAL and query the inner store directly

    @Override
    public boolean exists(String key) {
        return innerStore.exists(key);
    }

    @Override
    public long ttl(String key) {
        return innerStore.ttl(key);
    }

    @Override
    public String get(String key) {
        return innerStore.get(key);
    }

    @Override
    public List<String> getAllKeys() {
        return innerStore.getAllKeys();
    }

    @Override
    public List<String> prefixMatch(String prefix, int limit) {
        return innerStore.prefixMatch(prefix, limit);
    }

    @Override
    public List<String> range(String start, String end, int limit) {
        return innerStore.range(start, end, limit);
    }

    /**
     * Gracefully shuts down the background flusher, ensuring all pending
     * operations are persisted to disk before closing the file stream.
     */
    @Override
    public void close() {
        isRunning = false;
        try {
            flusherThread.join(2000);

            fileLock.lock();
            try {
                if (outStream != null) {
                    outStream.flush();
                    outStream.close();
                }
            } finally {
                fileLock.unlock();
            }
        } catch (Exception e) {
            System.err.println("Error closing WAL: " + e.getMessage());
        }
    }
}