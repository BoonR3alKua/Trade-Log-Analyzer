import java.io.File;
import java.util.Scanner;

/**
 * Main Trade Log Analyzer & Alert Engine Application
 * Orchestrates the log monitoring, parsing, and alerting system.
 */
public class LogAnalyzer {
    private final LogFileMonitor fileMonitor;
    private final AlertDispatcher alertDispatcher;
    private final String logFilePath;
    private volatile boolean stopped;

    public LogAnalyzer(String logFilePath) {
        this.logFilePath = logFilePath;
        this.alertDispatcher = new AlertDispatcher();
        this.fileMonitor = new LogFileMonitor(logFilePath, alertDispatcher);
        this.stopped = false;
    }

    /**
     * Starts the analyzer
     */
    public void start() {
        System.out.println("\n" + separator());
        System.out.println("TRADE LOG ANALYZER & ALERT ENGINE - STARTING");
        System.out.println(separator());
        System.out.println("Log File: " + logFilePath);
        System.out.println();

        // Verify log file exists
        File file = new File(logFilePath);
        if (!file.exists()) {
            System.err.println("ERROR: Log file not found at: " + logFilePath);
            System.err.println("Please ensure the log file exists before starting the analyzer.");
            return;
        }

        // Start components
        alertDispatcher.start();
        fileMonitor.start();
        fileMonitor.awaitInitialRead();

        System.out.println("\n✓ Analyzer started successfully");
        System.out.println("✓ Monitoring log file for errors");
        System.out.println();
    }

    /**
     * Stops the analyzer
     */
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;

        System.out.println("\n" + separator());
        System.out.println("STOPPING ANALYZER");
        System.out.println(separator());

        fileMonitor.stop();
        alertDispatcher.stop();

        System.out.println("✓ Analyzer stopped");
        System.out.println();
    }

    private String separator() {
        StringBuilder separator = new StringBuilder(80);
        for (int index = 0; index < 80; index++) {
            separator.append('=');
        }
        return separator.toString();
    }

    /**
     * Displays the interactive menu
     */
    private void displayMenu() {
        System.out.println("\n--- TRADE LOG ANALYZER MENU ---");
        System.out.println("1. Start monitoring");
        System.out.println("2. Add error code to track");
        System.out.println("3. Remove error code from tracking");
        System.out.println("4. View tracked error codes");
        System.out.println("5. View system status");
        System.out.println("6. Exit");
        System.out.print("Select option: ");
    }

    /**
     * Waits for the hidden command that returns from monitoring to the menu.
     */
    private boolean runMonitoringMode(Scanner scanner) {
        System.out.println("\nMonitoring is running.");
        System.out.println("Type menu and press Enter to return to the menu.");
        System.out.print("Monitoring command: ");

        while (scanner.hasNextLine()) {
            String command = scanner.nextLine().trim();
            if ("menu".equalsIgnoreCase(command)) {
                return true;
            }
            if (!command.isEmpty()) {
                System.out.println("Unknown monitoring command. Type menu and press Enter.");
            }
            System.out.print("Monitoring command: ");
        }

        return false;
    }

    /**
     * Runs the interactive command interface
     */
    public void runInteractive() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n✓ Select 'Start monitoring' when you are ready to process the log");
        
        boolean running = true;
        while (running) {
            displayMenu();

            try {
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        start();
                        if (!runMonitoringMode(scanner)) {
                            running = false;
                        }
                        break;
                    case "2":
                        handleAddErrorCode(scanner);
                        break;
                    case "3":
                        handleRemoveErrorCode(scanner);
                        break;
                    case "4":
                        handleViewErrorCodes();
                        break;
                    case "5":
                        handleViewStatus();
                        break;
                    case "6":
                        running = false;
                        System.out.println("Exiting...");
                        break;
                    case "help":
                        displayMenu();
                        break;
                    default:
                        System.out.println("Invalid option. Please try again.");
                        break;
                }
            } catch (Exception e) {
                System.err.println("Error reading input: " + e.getMessage());
            }
        }

        scanner.close();
    }

    /**
     * Handles adding a new error code to track
     */
    private void handleAddErrorCode(Scanner scanner) {
        System.out.print("Enter error code to track (e.g., CONNECTION_DROPPED): ");
        String errorCode = scanner.nextLine().trim().toUpperCase();

        if (errorCode.isEmpty()) {
            System.out.println("Error code cannot be empty");
            return;
        }

        fileMonitor.trackErrorCode(errorCode);
        System.out.println("✓ Now tracking: " + errorCode);
    }

    /**
     * Handles removing an error code from tracking
     */
    private void handleRemoveErrorCode(Scanner scanner) {
        System.out.print("Enter error code to stop tracking: ");
        String errorCode = scanner.nextLine().trim().toUpperCase();

        if (errorCode.isEmpty()) {
            System.out.println("Error code cannot be empty");
            return;
        }

        fileMonitor.untrackErrorCode(errorCode);
        System.out.println("✓ Stopped tracking: " + errorCode);
    }

    /**
     * Displays all tracked error codes
     */
    private void handleViewErrorCodes() {
        System.out.println("\n--- TRACKED ERROR CODES ---");
        java.util.Set<String> codes = fileMonitor.getTrackedErrorCodes();
        
        if (codes.isEmpty()) {
            System.out.println("No error codes being tracked");
        } else {
            codes.forEach(code -> System.out.println("  • " + code));
        }
        System.out.println();
    }

    /**
     * Displays system status
     */
    private void handleViewStatus() {
        System.out.println("\n--- SYSTEM STATUS ---");
        System.out.println("Log File Monitor: " + (fileMonitor.isRunning() ? "✓ RUNNING" : "✗ STOPPED"));
        System.out.println("Alert Dispatcher: " + (alertDispatcher.isRunning() ? "✓ RUNNING" : "✗ STOPPED"));
        System.out.println("Alerts in Queue: " + alertDispatcher.getQueueSize());
        System.out.println("Tracking " + fileMonitor.getTrackedErrorCodes().size() + " error codes");
        System.out.println("Log File: " + logFilePath);
        System.out.println();
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        String logFilePath = args.length > 0 ? args[0] : "trade_log.txt";

        if (!new File(logFilePath).exists()) {
            System.err.println("Please create the log file '" + logFilePath + "' first.");
            return;
        }

        // Create analyzer and configure default tracking before showing the menu
        final LogAnalyzer analyzer = new LogAnalyzer(logFilePath);

        // Add some default tracked error codes
        analyzer.fileMonitor.trackErrorCode("CONNECTION_DROPPED");
        analyzer.fileMonitor.trackErrorCode("TRADE_REJECTED");
        analyzer.fileMonitor.trackErrorCode("DATABASE_ERROR");

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                analyzer.stop();
            }
        }, "LogAnalyzer-ShutdownHook"));

        analyzer.runInteractive();
        analyzer.stop();
    }
}
