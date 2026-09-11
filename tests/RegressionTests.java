import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Dependency-free regression suite. Failures exit nonzero; fixtures live in a temp directory. */
public class RegressionTests {
    private interface Test { void run() throws Exception; }
    private static int passed;
    private static final String ERROR = "[2026-09-10 09:00:00] ERROR - DATABASE_ERROR: ";

    public static void main(String[] args) throws Exception {
        checkTest("strict parser and severity", RegressionTests::parser);
        checkTest("shutdown drains callbacks despite listener failure", RegressionTests::drain);
        checkTest("partial UTF-8 lines, filtering and no duplicates", RegressionTests::partial);
        checkTest("truncation resets offset", RegressionTests::truncate);
        checkTest("same-size file replacement resets offset", RegressionTests::replace);
        checkTest("bounded queue retries the unaccepted log line", RegressionTests::backpressure);
        checkTest("concurrent tracking updates", RegressionTests::tracking);
        checkTest("monitor worker starts and stops", RegressionTests::lifecycle);
        checkTest("CLI exits cleanly on EOF", RegressionTests::eof);
        checkTest("CLI shows menu before input and accepts commands while monitoring", RegressionTests::cli);
        checkTest("generator writes parseable UTF-8 records and validates counts", RegressionTests::generator);
        System.out.println("PASS: " + passed + " regression tests");
    }

    private static void checkTest(String name, Test test) throws Exception {
        PrintStream original = System.out;
        PrintStream sink = new PrintStream(new OutputStream() { public void write(int b) {} });
        try {
            System.setOut(sink);
            test.run();
            passed++;
        } finally {
            System.setOut(original);
            sink.close();
        }
        System.out.println("PASS: " + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void parser() {
        LogParser parser = new LogParser();
        check(parser.parseLine(ERROR + "ok").isErrorEvent(), "ERROR severity");
        check(!parser.parseLine(ERROR.replace("ERROR -", "WARN -") + "ok").isErrorEvent(), "WARN severity");
        check(parser.parseLine(ERROR.replace("2026-09-10", "2026-02-30") + "ok") == null, "invalid date");
        check(parser.parseLine("prefix " + ERROR + "ok") == null, "prefix accepted");
        check(parser.parseLine(ERROR + "   ") == null, "blank message accepted");
        check(parser.parseLine(null) == null, "null accepted");
        check(parser.parseLine(ERROR.replace("DATABASE_ERROR", "ERROR_42") + "ok") != null, "numeric code");
        check(parser.parseLine(ERROR.replace("2026-09-10", "2024-02-29") + "ok") != null, "leap day");
        LogEntry fatal = parser.parseLine(ERROR.replace("ERROR -", "FATAL -") + "ok");
        check(new Alert("DATABASE_ERROR", "test", fatal).getSeverity().equals("CRITICAL"), "FATAL mapping");
    }

    private static Alert alert() { return new Alert("DATABASE_ERROR", "test", new LogParser().parseLine(ERROR + "test")); }

    private static void drain() {
        AlertDispatcher dispatcher = new AlertDispatcher();
        List<Alert> received = Collections.synchronizedList(new ArrayList<Alert>());
        dispatcher.addListener(a -> { throw new IllegalStateException("intentional test failure"); });
        dispatcher.addListener(received::add);
        dispatcher.start();
        try { for (int i = 0; i < 10; i++) dispatcher.queueAlert(alert()); }
        finally { dispatcher.stop(); }
        check(received.size() == 10, "queued callbacks lost on shutdown");
        check(!dispatcher.isRunning() && dispatcher.getQueueSize() == 0, "dispatcher did not stop");
        dispatcher.stop();
        boolean rejected = false;
        try { dispatcher.queueAlert(alert()); } catch (IllegalStateException e) { rejected = true; }
        check(rejected, "enqueue after shutdown accepted");
    }

    private static class Fixture implements AutoCloseable {
        final Path dir = Files.createTempDirectory("trade-log-test-");
        final Path path = dir.resolve("trade.log");
        final AlertDispatcher dispatcher = new AlertDispatcher();
        final List<Alert> received = Collections.synchronizedList(new ArrayList<Alert>());
        final LogFileMonitor monitor;
        Fixture() throws Exception {
            Files.createFile(path);
            dispatcher.addListener(received::add);
            dispatcher.start();
            monitor = new LogFileMonitor(path.toString(), dispatcher);
            monitor.trackErrorCode("database_error");
        }
        void append(String text) throws Exception {
            Files.write(path, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        }
        public void close() throws Exception {
            monitor.stop();
            dispatcher.stop();
            Files.deleteIfExists(path);
            Files.deleteIfExists(dir.resolve("replacement.log"));
            Files.delete(dir);
        }
    }

    private static void partial() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = (ERROR + "caf\u00e9\r\n").getBytes(StandardCharsets.UTF_8);
            // Split inside the multibyte character, then before the CRLF terminator.
            Files.write(f.path, java.util.Arrays.copyOf(bytes, bytes.length - 3));
            f.monitor.processLogFile();
            check(f.monitor.getLastReadPosition() == 0, "partial UTF-8 line consumed");
            Files.write(f.path, java.util.Arrays.copyOfRange(bytes, bytes.length - 3, bytes.length), StandardOpenOption.APPEND);
            f.append(ERROR.replace("ERROR -", "INFO -") + "ignore\n");
            f.append(ERROR.replace("DATABASE_ERROR", "UNTRACKED") + "ignore\n");
            f.monitor.processLogFile();
            f.monitor.processLogFile();
            f.dispatcher.stop();
            check(f.received.size() == 1, "filter or duplicate regression");
            check(f.received.get(0).getTriggeringEntry().getMessage().equals("caf\u00e9"), "UTF-8 corruption");
        }
    }

    private static void truncate() throws Exception {
        try (Fixture f = new Fixture()) {
            f.append(ERROR + "a long first message\n");
            f.monitor.processLogFile();
            Files.write(f.path, (ERROR + "short\n").getBytes(StandardCharsets.UTF_8));
            f.monitor.processLogFile();
            f.dispatcher.stop();
            check(f.received.size() == 2, "truncated log not read");
        }
    }

    private static void replace() throws Exception {
        try (Fixture f = new Fixture()) {
            f.append(ERROR + "first\n");
            f.monitor.processLogFile();
            Path replacement = f.dir.resolve("replacement.log");
            Files.write(replacement, (ERROR + "other\n").getBytes(StandardCharsets.UTF_8));
            Files.move(replacement, f.path, StandardCopyOption.REPLACE_EXISTING);
            f.monitor.processLogFile();
            f.dispatcher.stop();
            check(f.received.size() == 2, "same-size replacement skipped");
        }
    }

    private static void backpressure() throws Exception {
        try (Fixture f = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            f.dispatcher.addListener(a -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            try {
                f.dispatcher.queueAlert(alert());
                check(entered.await(5, TimeUnit.SECONDS), "consumer did not enter callback");
                for (int i = 0; i < 1024; i++) f.dispatcher.queueAlert(alert());
                f.append(ERROR + "retry me\n");
                f.monitor.processLogFile();
                check(f.monitor.getLastReadPosition() == 0, "rejected line consumed");
            } finally { release.countDown(); }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (f.dispatcher.getQueueSize() > 0 && System.nanoTime() < deadline) Thread.yield();
            check(f.dispatcher.getQueueSize() == 0, "queue did not drain");
            f.monitor.processLogFile();
            f.dispatcher.stop();
            check(f.received.size() == 1026, "retry was lost or duplicated");
            check(f.monitor.getLastReadPosition() == Files.size(f.path), "retry offset not committed");
        }
    }

    private static void tracking() throws Exception {
        try (Fixture f = new Fixture()) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread updater = new Thread(() -> {
                try {
                    for (int i = 0; i < 10000; i++) {
                        f.monitor.trackErrorCode("CODE_" + i);
                        f.monitor.untrackErrorCode("CODE_" + i);
                    }
                } catch (Throwable e) { failure.set(e); }
            });
            updater.start();
            for (int i = 0; i < 1000; i++) f.monitor.getTrackedErrorCodes();
            updater.join();
            check(failure.get() == null, "concurrent update failed");
            f.monitor.getTrackedErrorCodes().clear();
            check(f.monitor.getTrackedErrorCodes().contains("DATABASE_ERROR"), "mutable state escaped");
        }
    }

    private static void lifecycle() throws Exception {
        try (Fixture f = new Fixture()) {
            f.append(ERROR + "startup\n");
            f.monitor.start();
            f.monitor.awaitInitialRead();
            f.monitor.stop();
            f.dispatcher.stop();
            check(!f.monitor.isRunning(), "monitor still running");
            check(f.received.size() == 1, "initial scan failed");
        }
    }

    private static void eof() throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String javaExecutable = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", executable).toString();
        Path output = Files.createTempFile("trade-cli-test-", ".txt");
        Process process = null;
        try {
            process = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
                    "LogAnalyzer", "examples/demo.log").redirectErrorStream(true).redirectOutput(output.toFile()).start();
            process.getOutputStream().close();
            check(process.waitFor(5, TimeUnit.SECONDS), "CLI loops on EOF");
            check(process.exitValue() == 0, "CLI failed");
        } finally {
            if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
            Files.deleteIfExists(output);
        }
    }

    private static void cli() throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String javaExecutable = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", executable).toString();
        Path output = Files.createTempFile("trade-cli-menu-", ".txt");
        Process process = null;
        try {
            process = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
                    "LogAnalyzer", "examples/demo.log").redirectErrorStream(true).redirectOutput(output.toFile()).start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!new String(Files.readAllBytes(output), StandardCharsets.UTF_8).contains("6. Exit")
                    && System.nanoTime() < deadline) Thread.sleep(10);
            check(new String(Files.readAllBytes(output), StandardCharsets.UTF_8).contains("6. Exit"),
                    "menu waits for input before displaying");
            process.getOutputStream().write("1\n2\ncode_42\n4\n3\ncode_42\n5\n6\n".getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            check(process.waitFor(10, TimeUnit.SECONDS), "CLI commands did not finish: "
                    + new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
            String text = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            check(process.exitValue() == 0 && text.contains("CODE_42") && text.contains("Monitor: true"),
                    "runtime tracking or status failed: " + text);
            check(text.contains("HIGH") && text.contains("CRITICAL"), "demo alerts missing");
        } finally {
            if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
            Files.deleteIfExists(output);
        }
    }

    private static void generator() throws Exception {
        Path path = Files.createTempFile("trade-generator-", ".log");
        try {
            LogGeneratorUtility generator = new LogGeneratorUtility(path.toString());
            generator.startContinuousGeneration(0, 5);
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            check(lines.size() == 5, "wrong generated count");
            for (String line : lines) check(new LogParser().parseLine(line) != null, "invalid generated line");
            generator.appendLogEntry(ERROR + "caf\u00e9");
            check(Files.readAllLines(path, StandardCharsets.UTF_8).get(5).equals(ERROR + "caf\u00e9"), "generator encoding");
            for (int[] invalid : new int[][] {{-1, 1}, {0, -1}}) {
                boolean rejected = false;
                try { generator.startContinuousGeneration(invalid[0], invalid[1]); }
                catch (IllegalArgumentException e) { rejected = true; }
                check(rejected, "negative generation argument accepted");
            }
            check(Files.readAllLines(path, StandardCharsets.UTF_8).size() == 6, "invalid arguments wrote data");
        } finally { Files.deleteIfExists(path); }
    }
}
