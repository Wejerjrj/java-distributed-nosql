package store;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A decorator that implements Write-Ahead Logging (WAL) for fault tolerance.
 * Includes automatic log compaction (Snapshotting) to prevent infinite file growth.
 */
public class WalDecorator implements KeyValueStore, AutoCloseable {

    private static final int COMPACTION_THRESHOLD = 10000;

    private final KeyValueStore innerStore;
    private final String logFilePath;
    private BufferedWriter writer;
    private final Lock fileLock = new ReentrantLock();
    private int operationCount = 0;

    /**
     * Constructs a new WalDecorator.
     *
     * @param innerStore  The underlying storage engine to decorate.
     * @param logFilePath The path to the append-only log file.
     */
    public WalDecorator(KeyValueStore innerStore, String logFilePath) {
        this.innerStore = innerStore;
        this.logFilePath = logFilePath;
        try {
            this.writer = new BufferedWriter(new FileWriter(logFilePath, true));
        } catch (IOException e) {
            System.err.println("Fatal error: Cannot initialize WAL - " + e.getMessage());
        }
    }

    /**
     * Reads the log file and replays all state-changing commands to reconstruct the memory state.
     */
    public void replayLog() {
        File file = new File(logFilePath);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    String[] parts = line.split("\\s+");
                    if (parts.length == 0) {
                        continue;
                    }

                    String command = parts[0].toUpperCase();

                    if (command.equals("SET") && parts.length >= 3) {
                        String[] split3 = line.split("\\s+", 3);
                        innerStore.set(split3[1], split3[2]);
                        operationCount++;
                    } else if (command.equals("SET_EX") && parts.length >= 4) {
                        String secondsStr = parts[parts.length - 1];
                        int seconds = Integer.parseInt(secondsStr);
                        String[] split3 = line.split("\\s+", 3);
                        String remainder = split3[2];
                        String value = remainder.substring(0, remainder.lastIndexOf(secondsStr)).trim();
                        innerStore.setEx(parts[1], value, seconds);
                        operationCount++;
                    } else if (command.equals("DELETE") && parts.length == 2) {
                        innerStore.delete(parts[1]);
                        operationCount++;
                    } else if (command.equals("INCR") && parts.length == 2) {
                        innerStore.incr(parts[1]);
                        operationCount++;
                    } else if (command.equals("FLUSHALL")) {
                        innerStore.flushAll();
                        operationCount++;
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Skipping corrupted WAL entry during replay.");
                }
            }
            System.out.println("WAL replay completed successfully. (" + operationCount + " operations restored)");
        } catch (IOException e) {
            System.err.println("Error during WAL replay: " + e.getMessage());
        }
    }

    /**
     * Compacts the WAL file by writing a clean snapshot of the current memory state.
     */
    public void compact() {
        fileLock.lock();
        try {
            if (writer != null) {
                writer.close();
            }

            File tempFile = new File(logFilePath + ".tmp");
            try (BufferedWriter tempWriter = new BufferedWriter(new FileWriter(tempFile, false))) {
                List<String> keys = innerStore.getAllKeys();
                for (String key : keys) {
                    String value = innerStore.get(key);
                    if (value == null) continue;

                    long ttl = innerStore.ttl(key);
                    if (ttl > 0) {
                        tempWriter.write("SET_EX " + key + " " + value + " " + ttl);
                    } else {
                        tempWriter.write("SET " + key + " " + value);
                    }
                    tempWriter.newLine();
                }
                tempWriter.flush();
            }

            File originalFile = new File(logFilePath);
            Files.move(tempFile.toPath(), originalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            this.writer = new BufferedWriter(new FileWriter(logFilePath, true));
            this.operationCount = innerStore.getAllKeys().size();
            System.out.println("WAL compaction completed. File size optimized.");

        } catch (IOException e) {
            System.err.println("Error during WAL compaction: " + e.getMessage());
        } finally {
            fileLock.unlock();
        }
    }

    private void checkCompaction() {
        operationCount++;
        if (operationCount >= COMPACTION_THRESHOLD) {
            compact();
        }
    }

    @Override
    public void set(String key, String value) {
        fileLock.lock();
        try {
            writer.write("SET " + key + " " + value);
            writer.newLine();
            writer.flush();
            checkCompaction();
        } catch (IOException e) {
            System.err.println("WAL write error (SET): " + e.getMessage());
        } finally {
            fileLock.unlock();
        }
        innerStore.set(key, value);
    }

    @Override
    public void setEx(String key, String value, int seconds) {
        fileLock.lock();
        try {
            writer.write("SET_EX " + key + " " + value + " " + seconds);
            writer.newLine();
            writer.flush();
            checkCompaction();
        } catch (IOException e) {
            System.err.println("WAL write error (SET_EX): " + e.getMessage());
        } finally {
            fileLock.unlock();
        }
        innerStore.setEx(key, value, seconds);
    }

    @Override
    public boolean delete(String key) {
        fileLock.lock();
        try {
            writer.write("DELETE " + key);
            writer.newLine();
            writer.flush();
            checkCompaction();
        } catch (IOException e) {
            System.err.println("WAL write error (DELETE): " + e.getMessage());
        } finally {
            fileLock.unlock();
        }
        return innerStore.delete(key);
    }

    @Override
    public boolean exists(String key) {
        return innerStore.exists(key);
    }

    @Override
    public Long incr(String key) {
        fileLock.lock();
        try {
            writer.write("INCR " + key);
            writer.newLine();
            writer.flush();
            checkCompaction();
        } catch (IOException e) {
            System.err.println("WAL write error (INCR): " + e.getMessage());
        } finally {
            fileLock.unlock();
        }
        return innerStore.incr(key);
    }

    @Override
    public long ttl(String key) {
        return innerStore.ttl(key);
    }

    @Override
    public void flushAll() {
        fileLock.lock();
        try {
            writer.write("FLUSHALL");
            writer.newLine();
            writer.flush();
            checkCompaction();
        } catch (IOException e) {
            System.err.println("WAL write error (FLUSHALL): " + e.getMessage());
        } finally {
            fileLock.unlock();
        }
        innerStore.flushAll();
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
    public List<String> prefixMatch(String prefix) {
        return innerStore.prefixMatch(prefix);
    }

    @Override
    public List<String> range(String start, String end) {
        return innerStore.range(start, end);
    }

    @Override
    public void close() {
        fileLock.lock();
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing WAL file: " + e.getMessage());
        } finally {
            fileLock.unlock();
        }
    }
}