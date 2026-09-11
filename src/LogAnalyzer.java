import java.util.Scanner;

/** CLI owns input; the monitor and dispatcher own background work. */
public class LogAnalyzer {
    private final AlertDispatcher dispatcher = new AlertDispatcher();
    private final LogFileMonitor monitor;
    private final String path;
    private boolean stopped;

    public LogAnalyzer(String path) {
        this.path = path;
        monitor = new LogFileMonitor(path, dispatcher);
        for (String code : new String[] {"CONNECTION_DROPPED", "TRADE_REJECTED", "DATABASE_ERROR"}) {
            monitor.trackErrorCode(code);
        }
    }

    public synchronized void start() {
        if (stopped) throw new IllegalStateException("Create a new analyzer after shutdown");
        if (monitor.isRunning()) return;
        dispatcher.start();
        monitor.start();
        monitor.awaitInitialRead();
        System.out.println("Monitoring " + path);
    }

    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        monitor.stop();
        dispatcher.stop();
    }

    private void displayMenu() {
        System.out.println("\n1. Start monitoring\n2. Add error code\n3. Remove error code"
                + "\n4. View tracked codes\n5. View status\n6. Exit");
    }

    public void runInteractive() {
        Scanner input = new Scanner(System.in);
        displayMenu();
        while (true) {
            System.out.print("> ");
            if (!input.hasNextLine()) return;
            String command = input.nextLine().trim();
            try {
                switch (command) {
                    case "1":
                        start();
                        break;
                    case "2":
                    case "3":
                        System.out.print("Error code: ");
                        if (!input.hasNextLine()) return;
                        String code = input.nextLine();
                        if (command.equals("2")) monitor.trackErrorCode(code);
                        else monitor.untrackErrorCode(code);
                        break;
                    case "4":
                        System.out.println(new java.util.TreeSet<>(monitor.getTrackedErrorCodes()));
                        break;
                    case "5":
                        System.out.printf("File: %s%nMonitor: %s | Dispatcher: %s | Queued: %d%n",
                                path, monitor.isRunning(), dispatcher.isRunning(), dispatcher.getQueueSize());
                        break;
                    case "6":
                        return;
                    case "menu":
                    case "help":
                        displayMenu();
                        break;
                    default:
                        System.out.println("Use 1-6, menu or help.");
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        LogAnalyzer analyzer = new LogAnalyzer(args.length > 0 ? args[0] : "trade_log.txt");
        Runtime.getRuntime().addShutdownHook(new Thread(analyzer::stop, "LogAnalyzer-ShutdownHook"));
        try {
            analyzer.runInteractive();
        } finally {
            analyzer.stop();
        }
    }
}