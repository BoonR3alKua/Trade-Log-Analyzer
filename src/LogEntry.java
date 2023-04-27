import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single entry from the trade log file.
 * Contains timestamp, log level, error code, and message.
 */
public class LogEntry {
    private LocalDateTime timestamp;
    private String logLevel;      // INFO, WARN, ERROR, FATAL
    private String errorCode;     // CONNECTION_DROPPED, TRADE_REJECTED, etc.
    private String message;
    private int severity;         // 1-4 (1=INFO, 4=FATAL)

    public LogEntry(LocalDateTime timestamp, String logLevel, String errorCode, String message) {
        this.timestamp = timestamp;
        this.logLevel = logLevel;
        this.errorCode = errorCode;
        this.message = message;
        this.severity = calculateSeverity(logLevel);
    }

    /**
     * Calculates numeric severity level from log level string
     */
    private int calculateSeverity(String logLevel) {
        switch (logLevel) {
            case "INFO":
                return 1;
            case "WARN":
                return 2;
            case "ERROR":
                return 3;
            case "FATAL":
                return 4;
            default:
                return 0;
        }
    }

    // Getters
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public int getSeverity() {
        return severity;
    }

    /**
     * Returns true if this entry represents an error or critical event
     */
    public boolean isErrorEvent() {
        return severity >= 3; // ERROR or FATAL
    }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s: %s", 
            timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            logLevel, errorCode, message);
    }
}
