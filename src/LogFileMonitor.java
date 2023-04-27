import java.io.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Monitors a log file continuously and triggers alerts for error codes.
 * Uses BufferedReader for efficient reading and supports growing files.
 */
public class LogFileMonitor implements Runnable {
    private final String logFilePath;
    private final AlertDispatcher alertDispatcher;
    private final LogParser logParser;
    private final Set<String> trackedErrorCodes;
    private volatile boolean running;
    private Thread monitorThread;
    private long lastReadPosition;
    private final CountDownLatch initialReadComplete;

    public LogFileMonitor(String logFilePath, AlertDispatcher alertDispatcher) {
        this.logFilePath = logFilePath;
        this.alertDispatcher = alertDispatcher;
        this.logParser = new LogParser();
        this.trackedErrorCodes = new HashSet<>();
        this.running = false;
        this.lastReadPosition = 0;
        this.initialReadComplete = new CountDownLatch(1);
    }

    /**
     * Adds an error code to track
     */
    public void trackErrorCode(String errorCode) {
        trackedErrorCodes.add(errorCode);
        System.out.println("Now tracking error code: " + errorCode);
    }

    /**
     * Removes an error code from tracking
     */
    public void untrackErrorCode(String errorCode) {
        trackedErrorCodes.remove(errorCode);
    }

    /**
     * Starts the log file monitoring thread
     */
    public synchronized void start() {
        if (running) {
            System.out.println("LogFileMonitor is already running");
            return;
        }

        // Verify file exists
        File file = new File(logFilePath);
        if (!file.exists()) {
            System.err.println("Error: Log file does not exist: " + logFilePath);
            return;
        }

        running = true;
        monitorThread = new Thread(this, "LogFileMonitor-Thread");
        monitorThread.setDaemon(false);
        monitorThread.start();
        System.out.println("LogFileMonitor started, monitoring: " + logFilePath);
    }

    /**
     * Stops the log file monitoring thread
     */
    public synchronized void stop() {
        if (!running) {
            System.out.println("LogFileMonitor is not running");
            return;
        }

        running = false;
        try {
            if (monitorThread != null) {
                monitorThread.join(5000); // Wait max 5 seconds
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted while stopping LogFileMonitor");
            Thread.currentThread().interrupt();
        }
        System.out.println("LogFileMonitor stopped");
    }

    /**
     * Main monitoring loop
     */
    @Override
    public void run() {
        System.out.println("Log File Monitor thread started");

        try {
            // Initial read of existing content
            processLogFile();
            initialReadComplete.countDown();

            // Keep monitoring for new content
            while (running) {
                try {
                    Thread.sleep(1000); // Check every second
                    processLogFile();
                } catch (InterruptedException e) {
                    if (running) {
                        System.err.println("LogFileMonitor interrupted");
                        Thread.currentThread().interrupt();
                    }
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Error in LogFileMonitor: " + e.getMessage());
            e.printStackTrace();
        } finally {
            initialReadComplete.countDown();
            System.out.println("Log File Monitor thread stopped");
        }
    }

    /**
     * Waits until the initial contents of the log file have been processed.
     */
    public void awaitInitialRead() {
        try {
            initialReadComplete.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Processes the log file - reads new lines and checks for errors
     */
    private void processLogFile() throws IOException {
        File file = new File(logFilePath);

        if (!file.exists()) {
            System.err.println("Log file no longer exists: " + logFilePath);
            return;
        }

        if (file.length() < lastReadPosition) {
            lastReadPosition = 0;
        }

        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            reader.seek(lastReadPosition);

            String line;
            while ((line = reader.readLine()) != null && running) {
                try {
                    processingLogLine(new String(line.getBytes("ISO-8859-1"), "UTF-8"));
                } catch (Exception e) {
                    System.err.println("Error processing line: " + line);
                    e.printStackTrace();
                }
            }

            lastReadPosition = reader.getFilePointer();

        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
            // Continue monitoring despite read errors
        }
    }

    /**
     * Processes a single log line
     */
    private void processingLogLine(String line) {
        // Parse the log entry
        LogEntry entry = logParser.parseLine(line);

        if (entry == null) {
            return; // Skip unparseable lines
        }

        System.out.println("[EVENT] " + entry);

        // Check if this is an error event
        if (entry.isErrorEvent()) {
            System.out.println("[DETECTED] " + entry);

            // Check if error code is tracked
            if (trackedErrorCodes.contains(entry.getErrorCode())) {
                System.out.println("[ALERT] Tracked error detected: " + entry.getErrorCode());

                // Create and queue alert
                Alert alert = new Alert(
                    entry.getErrorCode(),
                    "Trade log error detected: " + entry.getMessage(),
                    entry
                );

                alertDispatcher.queueAlert(alert);
            }
        }
    }

    /**
     * Returns the tracked error codes
     */
    public Set<String> getTrackedErrorCodes() {
        return new HashSet<>(trackedErrorCodes);
    }

    /**
     * Checks if monitoring is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the current file read position
     */
    public long getLastReadPosition() {
        return lastReadPosition;
    }
}
