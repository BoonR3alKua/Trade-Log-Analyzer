import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Immutable event shared by the monitor and dispatcher. */
public final class LogEntry {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final LocalDateTime timestamp;
    private final String logLevel;
    private final String errorCode;
    private final String message;

    public LogEntry(LocalDateTime timestamp, String logLevel, String errorCode, String message) {
        this.timestamp = timestamp;
        this.logLevel = logLevel;
        this.errorCode = errorCode;
        this.message = message;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public String getLogLevel() { return logLevel; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }

    public int getSeverity() {
        switch (logLevel) {
            case "INFO": return 1;
            case "WARN": return 2;
            case "ERROR": return 3;
            case "FATAL": return 4;
            default: return 0;
        }
    }

    public boolean isErrorEvent() { return getSeverity() >= 3; }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s: %s", timestamp.format(TIME), logLevel, errorCode, message);
    }
}