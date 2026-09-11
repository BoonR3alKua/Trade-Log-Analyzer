import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable notification and its source event. */
public final class Alert {
    private final String alertId = "ALERT_" + UUID.randomUUID();
    private final LocalDateTime alertTime = LocalDateTime.now();
    private final String errorCode;
    private final String description;
    private final LogEntry triggeringEntry;

    public Alert(String errorCode, String description, LogEntry triggeringEntry) {
        this.errorCode = errorCode;
        this.description = description;
        this.triggeringEntry = triggeringEntry;
    }

    public String getAlertId() { return alertId; }
    public LocalDateTime getAlertTime() { return alertTime; }
    public String getErrorCode() { return errorCode; }
    public String getDescription() { return description; }
    public LogEntry getTriggeringEntry() { return triggeringEntry; }

    public String getSeverity() {
        switch (triggeringEntry.getSeverity()) {
            case 4: return "CRITICAL";
            case 3: return "HIGH";
            default: return "MEDIUM";
        }
    }

    @Override
    public String toString() {
        return String.format("ALERT [%s] %s | %s | %s%n  %s%n  %s",
                alertId, getSeverity(), errorCode, alertTime, description, triggeringEntry);
    }
}