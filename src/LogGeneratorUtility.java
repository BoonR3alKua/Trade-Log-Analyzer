import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.Scanner;

/** Appends synthetic records for a local demonstration. */
public class LogGeneratorUtility {
    private static final String[] LEVELS = {"INFO", "WARN", "ERROR", "FATAL"};
    private static final String[][] CODES = {
        {"TRADE_STARTED", "CONNECTION_OK", "TRADE_EXECUTED", "RECONNECTED", "RECOVERY_STARTED", "SYSTEM_ONLINE", "MARKET_CLOSE"},
        {"LATENCY_HIGH", "DATABASE_SLOW", "LOW_BALANCE", "RATE_LIMIT_WARNING"},
        {"CONNECTION_DROPPED", "TRADE_REJECTED", "DATABASE_ERROR", "AUTHENTICATION_FAILED", "TIMEOUT_EXCEEDED"},
        {"SYSTEM_DOWN", "CRITICAL_ERROR", "DATA_CORRUPTION"}
    };
    private final String path;
    private final Random random = new Random();

    public LogGeneratorUtility(String path) { this.path = path; }

    public void appendLogEntry(String entry) throws IOException {
        Files.write(Paths.get(path), (entry + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void appendRandomEntry() throws IOException {
        int roll = random.nextInt(100);
        int level = roll < 50 ? 0 : roll < 80 ? 1 : roll < 95 ? 2 : 3;
        String code = CODES[level][random.nextInt(CODES[level].length)];
        String entry = new LogEntry(LocalDateTime.now(), LEVELS[level], code, "Synthetic trade event").toString();
        appendLogEntry(entry);
        System.out.println(entry);
    }

    public void startContinuousGeneration(long intervalMs, int count) throws IOException, InterruptedException {
        if (intervalMs < 0 || count < 0) throw new IllegalArgumentException("Count and interval must be non-negative");
        for (int i = 0; i < count; i++) {
            appendRandomEntry();
            if (i + 1 < count) Thread.sleep(intervalMs);
        }
    }

    public void interactiveMode() throws IOException, InterruptedException {
        Scanner input = new Scanner(System.in);
        while (true) {
            System.out.print("\n1. Append one entry\n2. Append multiple entries\n3. Exit\n> ");
            if (!input.hasNextLine()) return;
            try {
                switch (input.nextLine().trim()) {
                    case "1":
                        appendRandomEntry();
                        break;
                    case "2":
                        System.out.print("Count: ");
                        if (!input.hasNextLine()) return;
                        int count = Integer.parseInt(input.nextLine().trim());
                        System.out.print("Interval (ms): ");
                        if (!input.hasNextLine()) return;
                        long interval = Long.parseLong(input.nextLine().trim());
                        startContinuousGeneration(interval, count);
                        break;
                    case "3":
                        return;
                    default:
                        System.out.println("Use 1-3.");
                }
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        try {
            LogGeneratorUtility generator = new LogGeneratorUtility(args.length > 0 ? args[0] : "trade_log.txt");
            if (args.length <= 1) {
                generator.interactiveMode();
            } else {
                if (!args[1].equals("--auto") || args.length > 4) {
                    throw new IllegalArgumentException("Usage: LogGeneratorUtility [path [--auto [count [intervalMs]]]]");
                }
                int count = args.length > 2 ? Integer.parseInt(args[2]) : 5;
                long interval = args.length > 3 ? Long.parseLong(args[3]) : 2000;
                generator.startContinuousGeneration(interval, count);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Generation interrupted");
            System.exit(1);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
