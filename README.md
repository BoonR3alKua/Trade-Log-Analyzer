# Trade Log Analyzer & Alert Engine

A dependency-free Java CLI that monitors trading-system logs and dispatches alerts on a separate thread. Notifications go to the console and registered callbacks.

## Build and demo

Build with JDK 11 or newer on PATH. The application targets Java 8 bytecode and APIs.

Windows:

```powershell
.\build.bat test
java -cp build/classes LogAnalyzer examples/demo.log
```

Linux / macOS:

```bash
bash build.sh test
java -cp build/classes LogAnalyzer examples/demo.log
```

Choose 1 to start. The sample produces a HIGH `TRADE_REJECTED` alert and a CRITICAL `DATABASE_ERROR` alert. Options 2 and 3 add or remove tracked codes, 4 lists them, 5 shows status, and 6 exits. Commands remain available during monitoring; `menu` or `help` displays them again. Closing standard input also exits.

For a live demo, copy `examples/demo.log` to `demo.log` with `Copy-Item` (Windows) or `cp` (Linux/macOS), then monitor the copy. In a second terminal:

```powershell
java -cp build/classes LogGeneratorUtility demo.log --auto 20 500
```

This appends 20 random records with 500 ms between records. Run the generator with just a path for its interactive menu. Counts and intervals must be non-negative; command-line errors exit nonzero.

## How it works

```text
CLI -> concurrent tracked-code set
                    |
LogFileMonitor -> LogParser -> LogEntry -> severity/code filter
                                              |
                                   bounded queue (1,024)
                                              |
                                       AlertDispatcher
                                              |
                                  console + listener callbacks
```

The monitor polls every second and reads from its committed byte offset. It waits for LF or CRLF before parsing a record, so split UTF-8 writes can finish. Malformed records are reported and skipped.

Only ERROR and FATAL events with tracked codes create alerts. Defaults are `CONNECTION_DROPPED`, `TRADE_REJECTED`, and `DATABASE_ERROR`. Subscription input is normalized to uppercase.

```text
[2026-09-10 09:00:02] ERROR - TRADE_REJECTED: Order validation failed
```

The parser validates calendar dates strictly. Supported levels are INFO, WARN, ERROR, and FATAL. Codes start with a letter and contain uppercase letters, digits, or underscores. Messages must contain non-whitespace text.

The dispatcher uses `ArrayBlockingQueue`; listener registration uses `CopyOnWriteArrayList`. If the queue fills, the monitor retains the unaccepted record's offset and retries on its next scan. Shutdown stops the monitor, then drains accepted alerts and callbacks. Worker instances are single-use.

Register a listener with `dispatcher.addListener(alert -> { /* handle alert */ });`. Callbacks run sequentially; exceptions are logged without preventing other listeners from running.

## Verification

`build.bat test` or `bash build.sh test` runs 11 regression scenarios covering parsing, severity filtering, shutdown, callback failure, partial UTF-8 records, truncation, same-size replacement, queue overflow retry, concurrent tracking, worker lifecycle, CLI input, and generator validation. Tests use temporary files and leave `trade_log.txt` alone.

GitHub Actions is configured for Windows and Linux on JDK 11 and 21. Omitting `test` builds only the application. Output goes into ignored `build/classes/`.

## Limits

- Alerts and offsets live in memory. Restarting replays the file; crash recovery and exactly-once delivery are not provided.
- Shutdown drains accepted alerts, not unread file contents. The source log must remain available until its backlog is read.
- A callback that never returns prevents graceful shutdown from completing. Failed callbacks are not retried.
- Rotation detection checks file identity, observed shrinkage, and the first 256 committed bytes. A replacement with the same prefix and no usable identity can be missed, as can truncation followed by regrowth between polls. Retired files are not drained.
- An unterminated final record waits for a newline. Individual line lengths are not bounded.
- This is a local educational project, with no email integration, persistent storage, or measured throughput claim.