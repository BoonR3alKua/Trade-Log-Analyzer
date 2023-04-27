import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.Scanner;

/**
 * Utility to generate test log entries and append them to the log file.
 * Useful for simulating live trading logs for testing the analyzer.
 */
public class LogGeneratorUtility {
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private static final String[] INFO_CODES = {
        "TRADE_STARTED", "CONNECTION_OK", "TRADE_EXECUTED", 
        "RECONNECTED", "RECOVERY_STARTED", "SYSTEM_ONLINE", "MARKET_CLOSE"
    };
    
    private static final String[] WARN_CODES = {
        "LATENCY_HIGH", "DATABASE_SLOW", "LOW_BALANCE", "RATE_LIMIT_WARNING"
    };
    
    private static final String[] ERROR_CODES = {
        "CONNECTION_DROPPED", "TRADE_REJECTED", "DATABASE_ERROR", 
        "AUTHENTICATION_FAILED", "TIMEOUT_EXCEEDED"
    };
    
    private static final String[] FATAL_CODES = {
        "SYSTEM_DOWN", "CRITICAL_ERROR", "DATA_CORRUPTION"
    };
    
    private static final String[] MESSAGES = {
        "Operation completed",
        "Transaction pending",
        "Retry mechanism engaged",
        "Request timeout",
        "Service unavailable",
        "Resource exhausted",
        "Invalid parameters",
        "Insufficient permissions",
        "Cache miss detected",
        "Network error occurred"
    };

    private final String logFilePath;
    private final Random random;

    public LogGeneratorUtility(String logFilePath) {
        this.logFilePath = logFilePath;
        this.random = new Random();
    }

    /**
     * Generates a random log entry
     */
    private String generateLogEntry() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(FORMATTER);
        
        // Randomly select severity level
        int severityChoice = random.nextInt(100);
        String level, code;
        
        if (severityChoice < 50) {
            level = "INFO";
            code = INFO_CODES[random.nextInt(INFO_CODES.length)];
        } else if (severityChoice < 80) {
            level = "WARN";
            code = WARN_CODES[random.nextInt(WARN_CODES.length)];
        } else if (severityChoice < 95) {
            level = "ERROR";
            code = ERROR_CODES[random.nextInt(ERROR_CODES.length)];
        } else {
            level = "FATAL";
            code = FATAL_CODES[random.nextInt(FATAL_CODES.length)];
        }
        
        String message = MESSAGES[random.nextInt(MESSAGES.length)];
        
        return String.format("[%s] %s - %s: %s", timestamp, level, code, message);
    }

    /**
     * Appends a single log entry to the file
     */
    public void appendLogEntry(String entry) throws IOException {
        try (FileWriter writer = new FileWriter(logFilePath, true)) {
            writer.write(entry + "\n");
            writer.flush();
        }
    }

    /**
     * Generates and appends a random log entry
     */
    public void appendRandomEntry() throws IOException {
        String entry = generateLogEntry();
        appendLogEntry(entry);
        System.out.println("Added: " + entry);
    }

    /**
     * Continuously generates log entries at specified interval
     */
    public void startContinuousGeneration(long intervalMs, int count) {
        System.out.println("Starting log generation: " + count + " entries, interval: " + intervalMs + "ms");
        
        for (int i = 0; i < count; i++) {
            try {
                appendRandomEntry();
                Thread.sleep(intervalMs);
            } catch (IOException e) {
                System.err.println("Error writing to log file: " + e.getMessage());
                break;
            } catch (InterruptedException e) {
                System.err.println("Log generation interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println("Log generation completed");
    }

    /**
     * Interactive menu for log generation
     */
    public void interactiveMode() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.println("\n--- LOG GENERATOR UTILITY ---");
            System.out.println("1. Add single random log entry");
            System.out.println("2. Add multiple entries with interval");
            System.out.println("3. Exit");
            System.out.print("Select option: ");

            try {
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        appendRandomEntry();
                        break;
                    case "2":
                        System.out.print("Number of entries: ");
                        int count = Integer.parseInt(scanner.nextLine().trim());
                        System.out.print("Interval in milliseconds: ");
                        long interval = Long.parseLong(scanner.nextLine().trim());
                        startContinuousGeneration(interval, count);
                        break;
                    case "3":
                        running = false;
                        System.out.println("Exiting log generator");
                        break;
                    default:
                        System.out.println("Invalid option");
                        break;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid input format");
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        scanner.close();
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        String logFilePath = args.length > 0 ? args[0] : "trade_log.txt";
        
        LogGeneratorUtility generator = new LogGeneratorUtility(logFilePath);
        
        if (args.length > 1 && args[1].equals("--auto")) {
            // Auto mode: generate entries and exit
            int count = args.length > 2 ? Integer.parseInt(args[2]) : 5;
            long interval = args.length > 3 ? Long.parseLong(args[3]) : 2000;
            generator.startContinuousGeneration(interval, count);
        } else {
            // Interactive mode
            generator.interactiveMode();
        }
    }
}
