import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/** Polls complete UTF-8 lines, retaining the offset of an incomplete trailing line. */
public class LogFileMonitor implements Runnable {
    private final Path path;
    private final AlertDispatcher dispatcher;
    private final LogParser parser = new LogParser();
    private final Set<String> tracked = ConcurrentHashMap.newKeySet();
    private final CountDownLatch initialRead = new CountDownLatch(1);
    private volatile boolean running;
    private volatile long lastReadPosition;
    private Object fileKey;
    private byte[] prefix = new byte[0];
    private Thread worker;

    public LogFileMonitor(String logFilePath, AlertDispatcher alertDispatcher) {
        path = Paths.get(logFilePath);
        dispatcher = Objects.requireNonNull(alertDispatcher);
    }

    private String normalize(String code) {
        String value = Objects.requireNonNull(code, "errorCode").trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]*"))
            throw new IllegalArgumentException("Use letters, digits and underscores; start with a letter");
        return value;
    }

    public void trackErrorCode(String code) { tracked.add(normalize(code)); }
    public void untrackErrorCode(String code) { tracked.remove(normalize(code)); }

    public synchronized void start() {
        if (worker != null) return;
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Log file not found: " + path);
        running = true;
        worker = new Thread(this, "LogFileMonitor-Thread");
        worker.start();
    }

    public void stop() {
        Thread thread;
        synchronized (this) {
            running = false;
            thread = worker;
            if (thread != null) thread.interrupt();
        }
        if (thread == null || thread == Thread.currentThread()) return;
        boolean interrupted = false;
        while (thread.isAlive()) {
            try { thread.join(); }
            catch (InterruptedException e) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    @Override
    public void run() {
        try {
            while (running) {
                try { processLogFile(); }
                catch (IOException e) {
                    System.err.println("Cannot read log; retrying: " + e.getMessage());
                } finally { initialRead.countDown(); }
                try { Thread.sleep(1000); }
                catch (InterruptedException e) { break; }
            }
        } finally {
            running = false;
            initialRead.countDown();
        }
    }

    public void awaitInitialRead() {
        try { initialRead.await(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // Package-private for deterministic integration tests without timing-based sleeps.
    void processLogFile() throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        Object currentKey = attributes.fileKey();
        if ((fileKey != null && currentKey != null && !fileKey.equals(currentKey))
                || attributes.size() < lastReadPosition) {
            lastReadPosition = 0;
        }
        fileKey = currentKey;
        try (RandomAccessFile reader = new RandomAccessFile(path.toFile(), "r")) {
            // ponytail: prefix fallback misses identical-prefix replacements; use OS file identity for stronger rotation guarantees.
            if (lastReadPosition > 0 && prefix.length > 0) {
                byte[] currentPrefix = new byte[prefix.length];
                int count = reader.read(currentPrefix);
                if (count != prefix.length || !Arrays.equals(prefix, currentPrefix)) lastReadPosition = 0;
            }
            reader.seek(lastReadPosition);
            while (!Thread.currentThread().isInterrupted()) {
                String raw = reader.readLine();
                if (raw == null) break;
                long end = reader.getFilePointer();
                // readLine returns unterminated data at EOF. Wait for LF before parsing.
                reader.seek(end - 1);
                if (reader.read() != '\n') break;
                String line = new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                LogEntry entry = parser.parseLine(line);
                if (entry != null) {
                    System.out.println("[EVENT] " + entry);
                    if (entry.isErrorEvent() && tracked.contains(entry.getErrorCode())) {
                        try {
                            dispatcher.queueAlert(new Alert(entry.getErrorCode(),
                                    "Trade log error detected: " + entry.getMessage(), entry));
                        } catch (IllegalStateException e) {
                            // Retain this line's offset and retry on the next scan.
                            System.err.println(e.getMessage() + "; retaining log offset for retry");
                            break;
                        }
                    }
                }
                lastReadPosition = end;
            }
            prefix = new byte[(int) Math.min(lastReadPosition, 256)];
            reader.seek(0);
            reader.readFully(prefix);
        }
    }

    public Set<String> getTrackedErrorCodes() { return new HashSet<>(tracked); }
    public boolean isRunning() { return running; }
    public long getLastReadPosition() { return lastReadPosition; }
}
