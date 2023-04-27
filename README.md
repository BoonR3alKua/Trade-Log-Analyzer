# Trade Log Analyzer and Alert Engine

## Purpose

This Java 8-compatible console application watches a growing trade log, parses each new line, identifies `ERROR` and `FATAL` events, and creates alerts for tracked error codes. A monitor thread reads the file while a dispatcher thread processes alerts asynchronously. `LogGeneratorUtility` provides manual or automatic test data.

The project is educational and uses console output as its notification mechanism. It does not open network ports, send real email, or persist alerts to a database.

## Requirements

- Java 8 or later **JDK**, including `java` and `javac`
- Windows, Linux, or macOS
- A log file in the expected format

The build targets Java 8. A newer JDK can compile it with the `--release 8` option used by the supplied scripts.

## Build

From the project directory:

Windows:

```batch
build.bat
```

Linux or macOS:

```bash
chmod +x build.sh
./build.sh
```

The scripts compile the Java files into `bin/`. To compile directly:

```bash
javac --release 8 -d bin -encoding UTF-8 src/*.java
```

If `javac` is not found, install a JDK and add its `bin` directory to `PATH`. A JRE alone is not sufficient.

## Run

Start the analyzer in one terminal:

```powershell
java -cp bin LogAnalyzer trade_log.txt
```

The analyzer configures these default codes and shows its menu before starting any worker thread:

```text
1. Start monitoring
2. Add error code to track
3. Remove error code from tracking
4. View tracked error codes
5. View system status
6. Exit
```

Choose option `1` when you are ready to process the log. The initial scan finishes before monitoring mode becomes active. While monitoring, type the unlisted command `menu` to return to the control menu without stopping the worker threads. Choose option `6` for a clean shutdown. `Ctrl+C` is the fallback when the menu is unavailable.

In a second terminal, generate test data:

```powershell
java -cp bin LogGeneratorUtility trade_log.txt
```

Generator options:

1. Append one random entry.
2. Append a chosen number of entries with a chosen delay in milliseconds.
3. Exit.

For non-interactive generation:

```powershell
java -cp bin LogGeneratorUtility trade_log.txt --auto 20 500
```

This appends 20 entries with a 500 ms delay between entries.

## Log Format and Alert Rules

Each line must match:

```text
[YYYY-MM-DD HH:mm:ss] LOG_LEVEL - ERROR_CODE: message
```

Example:

```text
[2026-01-15 09:12:33] ERROR - CONNECTION_DROPPED: Lost connection to trading server
```

The parser accepts `INFO`, `WARN`, `ERROR`, and `FATAL`. Their numeric severity values are 1, 2, 3, and 4. Only severity 3 or 4 is an error event. An alert is created only when the event's error code is currently tracked. `ERROR` creates a `HIGH` alert and `FATAL` creates a `CRITICAL` alert.

## End-to-End Process

1. `LogAnalyzer.main` chooses the log path, constructs the analyzer, registers default error codes, installs the shutdown hook, and enters the CLI.
2. `LogFileMonitor.run` reads existing content once, then checks for appended content every second.
3. `LogParser.parseLine` validates a line with a regular expression, parses its timestamp, and creates a `LogEntry`.
4. `LogFileMonitor` prints valid events. For `ERROR` or `FATAL`, it checks the tracked-code set.
5. A matching event creates an `Alert` and sends it to the dispatcher queue.
6. `AlertDispatcher.run` removes queued alerts, prints the alert details, simulates a 500 ms notification delay, and calls registered listeners.
7. Option 6 stops the monitor and dispatcher. The shutdown hook also protects Ctrl+C termination; shutdown is guarded so it is not performed twice.

## Architecture

```text
LogAnalyzer main / CLI
        |
        +--> LogFileMonitor thread --> LogParser --> LogEntry
        |                                  |
        |                         tracked error code?
        |                                  |
        +----------------------------> Alert queue
                                           |
                                  AlertDispatcher thread
                                           |
                                  console notification
                                  AlertListener callbacks
```

`LogFileMonitor` is the producer. `AlertDispatcher` is the consumer. `ReentrantLock` protects the dispatcher queue and listener list. The monitor's read position allows the application to process only appended bytes and to reset safely if the file is truncated or rotated to a shorter file.

## Function Reference

The descriptions below reflect the current implementation, including private helpers. Methods are grouped by source file in the order used by the runtime.

### `LogEntry.java`

`LogEntry` is the value object passed from the parser to the monitor and then into an alert. Its fields hold the event timestamp, text log level, error code, message, and derived numeric severity.

- `LogEntry(LocalDateTime timestamp, String logLevel, String errorCode, String message)` stores the parsed values and immediately derives `severity` from `logLevel`.
- `calculateSeverity(String logLevel)` maps `INFO` to 1, `WARN` to 2, `ERROR` to 3, and `FATAL` to 4. Unknown levels produce 0.
- `getTimestamp()` returns the parsed `LocalDateTime`.
- `getLogLevel()` returns the original level name.
- `getErrorCode()` returns the uppercase identifier extracted from the line.
- `getMessage()` returns the text after the error-code colon.
- `getSeverity()` returns the numeric severity calculated by the constructor.
- `isErrorEvent()` returns `true` for severity 3 or higher, which means only `ERROR` and `FATAL` entries enter alert evaluation.
- `toString()` formats the object back into the standard log-line shape for event and diagnostic output.

### `LogParser.java`

`LogParser` converts text into `LogEntry` objects. Its pattern requires a timestamp, one supported level, an uppercase/underscore code, and a non-empty message.

- `parseLine(String line)` rejects null or blank input, matches the regular expression, parses the timestamp with `yyyy-MM-dd HH:mm:ss`, and returns a populated `LogEntry`. It returns `null` for malformed lines or invalid dates and writes a diagnostic to standard error.
- `containsErrorCode(String line, String errorCode)` performs a simple literal substring check. It does not parse the line and is separate from the monitor's parsed-code comparison.
- `extractErrorCode(String line)` delegates to `parseLine` and returns the parsed code, or `null` when parsing fails.

### `Alert.java`

`Alert` is the unit placed on the dispatcher queue. It captures both the operator-facing description and the original `LogEntry` that caused it.

- `Alert(String errorCode, String description, LogEntry triggeringEntry)` creates an ID, records the current creation time, stores the supplied data, starts unacknowledged, and derives alert severity from the triggering entry.
- `generateAlertId()` combines the current epoch millisecond and a random four-digit value to produce IDs such as `ALERT_1788510645317_2036`.
- `calculateSeverity(LogEntry entry)` maps entry severity 3 to `HIGH`, 4 to `CRITICAL`, and all other values to `MEDIUM`.
- `getAlertId()`, `getAlertTime()`, `getSeverity()`, `getErrorCode()`, `getDescription()`, and `getTriggeringEntry()` return the corresponding alert fields.
- `isAcknowledged()` reports whether an operator has acknowledged the alert.
- `acknowledge()` changes the alert state to acknowledged. The current CLI does not expose an acknowledge command, but listeners can use this API.
- `toString()` formats the ID, severity, pending/acknowledged state, code, creation time, description, and triggering log entry for console output.

### `AlertDispatcher.java`

`AlertDispatcher` owns the alert queue and runs its consumer loop on a separate non-daemon thread. Every access to the `LinkedList` queue and listener list is protected by `lock`.

- `AlertListener.onAlertTriggered(Alert alert)` defines the callback contract for email, web, database, or other notification integrations.
- `AlertDispatcher()` initializes the queue, listener list, lock, and stopped state.
- `start()` is synchronized, ignores duplicate starts, sets `running`, creates the named dispatcher thread, and starts it.
- `stop()` is synchronized, marks the dispatcher stopped, clears queued alerts, interrupts the worker, and waits up to five seconds for it to finish.
- `queueAlert(Alert alert)` locks the queue and appends an alert for later processing. It returns immediately, so the monitor is not held up by alert output.
- `run()` is the worker loop. It polls one alert under the lock, processes and notifies it when present, or sleeps 100 ms when idle. On exit it processes alerts still queued and then reports that the thread stopped.
- `processAlert(Alert alert)` prints the formatted alert surrounded by section separators and invokes the simulated notification.
- `separator()` creates the 80-character line used in dispatcher output.
- `simulateAlertNotification(Alert alert)` waits 500 ms to represent external notification work, then prints a dispatch confirmation. Interruption is reported and the interrupt flag is restored.
- `processPendingAlerts()` drains alerts remaining during shutdown. It is used by `run()` after the main loop ends.
- `addListener(AlertListener listener)` registers a callback under the lock.
- `removeListener(AlertListener listener)` removes a previously registered callback under the lock.
- `notifyListeners(Alert alert)` copies the listener list under the lock, then invokes callbacks outside the lock so a slow listener cannot block queue access.
- `getQueueSize()` returns the number of alerts currently waiting in the queue.
- `isRunning()` reports the volatile worker-running flag.

### `LogFileMonitor.java`

`LogFileMonitor` is the producer thread. It tracks a byte offset in the file, so existing lines are read once and newly appended lines are read on later passes.

- `LogFileMonitor(String logFilePath, AlertDispatcher alertDispatcher)` stores the file path and destination dispatcher, creates a parser and empty tracked set, and initializes the offset to zero.
- `trackErrorCode(String errorCode)` adds a code to the tracked set and reports the new subscription. The CLI normalizes user input to uppercase before calling it.
- `untrackErrorCode(String errorCode)` removes a code so future matching events no longer create alerts.
- `start()` verifies the file exists, sets `running`, creates the non-daemon monitor thread, and starts it. Duplicate starts are ignored.
- `stop()` clears `running` and waits up to five seconds for the monitor thread to finish its current pass.
- `run()` performs one immediate read, signals that startup scanning is complete, then sleeps for one second between later reads. It handles I/O failures and interruption before reporting termination.
- `processLogFile()` opens the file with `RandomAccessFile`, seeks to `lastReadPosition`, processes each available line, and records the new file pointer. If the file became shorter, it resets the offset to zero. Malformed individual lines do not stop the pass.
- `processingLogLine(String line)` parses and prints an event, filters to error events, checks the tracked set, creates a matching `Alert`, and queues it.
- `getTrackedErrorCodes()` returns a copy of the set so callers cannot mutate monitor state directly.
- `isRunning()` reports whether the monitor loop should continue.
- `getLastReadPosition()` exposes the current byte offset for diagnostics.
- `awaitInitialRead()` blocks the analyzer's main thread until the first complete scan has finished, preventing the CLI menu from being mixed into startup log output.

### `LogAnalyzer.java`

`LogAnalyzer` coordinates the monitor, dispatcher, defaults, and operator CLI. The main thread owns command input while the two workers produce output.

- `LogAnalyzer(String logFilePath)` constructs the dispatcher and monitor with the same log path and initializes the one-time shutdown guard.
- `start()` verifies the log file and starts both worker components. It returns without starting them when the file is absent.
- `stop()` performs a guarded shutdown: prints a header, stops the monitor, stops the dispatcher, and confirms completion. Repeated calls are ignored so normal option-5 exit and the JVM shutdown hook do not duplicate output.
- `separator()` creates the 80-character startup/shutdown divider.
- `displayMenu()` prints the five analyzer commands and the input prompt.
- `runMonitoringMode(Scanner scanner)` waits for the unlisted `menu` command, returning control to the menu while the worker threads keep running. End of input also leaves the application loop.
- `runInteractive()` displays the pre-start menu, starts monitoring only after option 1, dispatches tracking, status, and exit commands, and closes its scanner when option 6 ends the loop. `help` redisplays the menu.
- `handleAddErrorCode(Scanner scanner)` prompts for a code, trims and converts it to uppercase, rejects blank input, and registers it with the monitor.
- `handleRemoveErrorCode(Scanner scanner)` performs the same normalization and validation, then unregisters the code.
- `handleViewErrorCodes()` obtains the monitor's defensive copy and prints all tracked codes or a no-codes message.
- `handleViewStatus()` reports monitor state, dispatcher state, queue length, tracked-code count, and log path.
- `main(String[] args)` selects the first argument or `trade_log.txt`, exits early when the file is missing, registers the three default codes, installs the Ctrl+C shutdown hook, enters `runInteractive`, and finally calls `stop()`.

### `LogGeneratorUtility.java`

`LogGeneratorUtility` appends synthetic entries to a selected file. It chooses levels with a 50% `INFO`, 30% `WARN`, 15% `ERROR`, and 5% `FATAL` distribution.

- `LogGeneratorUtility(String logFilePath)` stores the output path and creates the random-number generator.
- `generateLogEntry()` chooses the current timestamp, level, matching code array, and message, then formats a valid log line. It is private because callers use the append methods instead of receiving an unpersisted line.
- `appendLogEntry(String entry)` opens the file in append mode, writes the supplied line plus a newline, flushes, and closes the writer.
- `appendRandomEntry()` generates one line, appends it, and prints the result.
- `startContinuousGeneration(long intervalMs, int count)` repeats random appends `count` times, sleeping `intervalMs` after each append. I/O and interruption stop the loop with a diagnostic.
- `interactiveMode()` reads generator commands until option 3, validates the count and interval for option 2, and reports invalid numeric input.
- `main(String[] args)` selects the output path. With `--auto` as the second argument it parses optional count and interval values; otherwise it starts the interactive generator menu.

## Extending the Application

Register a listener to connect real notification infrastructure:

```java
dispatcher.addListener(new AlertDispatcher.AlertListener() {
    @Override
    public void onAlertTriggered(Alert alert) {
        // Send email, call a webhook, or persist the alert here.
    }
});
```

Other natural extensions include configurable tracked codes, log rotation handling, durable alert storage, metrics, and a web dashboard.

## Troubleshooting

**`javac` not found**: install a JDK, then open a new terminal or add the JDK `bin` directory to `PATH`.

**Log file not found**: run from the project directory or pass an absolute path as the first argument. The file must exist before the analyzer starts.

**No alerts**: confirm the error code appears in menu option 3, the line uses the exact supported format, and its level is `ERROR` or `FATAL`.

**Menu not visible**: rebuild the classes and verify the current `LogAnalyzer.main` calls `runInteractive()`. Worker event output can interleave with the prompt, but it should not prevent input.

**Shutdown appears delayed**: the dispatcher may be finishing its current notification or pending alert. Its join timeout is five seconds.

## Project Layout

```text
src/                         Java source files
bin/                         Compiled class files
trade_log.txt                Sample and active log
build.bat                    Windows build script
build.sh                     Linux/macOS build script
README.md                    Canonical project documentation
```

## Learning Topics

The project demonstrates regular expressions, `LocalDateTime`, random-access file reading, append-only file writing, `Runnable` threads, volatile state, `ReentrantLock`, producer-consumer queues, observer callbacks, defensive copies, exception handling, and graceful shutdown.
