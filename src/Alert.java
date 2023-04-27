import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents an alert triggered when an error condition is detected.
 */
public class Alert {
    private String alertId;
    private LocalDateTime alertTime;
    private String severity;        // CRITICAL, HIGH, MEDIUM, LOW
    private String errorCode;
    private String description;
    private LogEntry triggeringEntry;
    private boolean acknowledged;

    public Alert(String errorCode, String description, LogEntry triggeringEntry) {
        this.alertId = generateAlertId();
        this.alertTime = LocalDateTime.now();
        this.errorCode = errorCode;
        this.description = description;
        this.triggeringEntry = triggeringEntry;
        this.acknowledged = false;
        this.severity = calculateSeverity(triggeringEntry);
    }

    /**
     * Generates unique alert ID
     */
    private String generateAlertId() {
        return "ALERT_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    /**
     * Determines alert severity based on log entry severity
     */
    private String calculateSeverity(LogEntry entry) {
        switch (entry.getSeverity()) {
            case 3:
                return "HIGH";
            case 4:
                return "CRITICAL";
            default:
                return "MEDIUM";
        }
    }

    // Getters and setters
    public String getAlertId() {
        return alertId;
    }

    public LocalDateTime getAlertTime() {
        return alertTime;
    }

    public String getSeverity() {
        return severity;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDescription() {
        return description;
    }

    public LogEntry getTriggeringEntry() {
        return triggeringEntry;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void acknowledge() {
        this.acknowledged = true;
    }

    @Override
    public String toString() {
        return String.format(
            "ALERT [%s] - %s (%s severity)\n" +
            "  Error Code: %s\n" +
            "  Time: %s\n" +
            "  Description: %s\n" +
            "  Triggered By: %s",
            alertId,
            severity,
            acknowledged ? "ACKNOWLEDGED" : "PENDING",
            errorCode,
            alertTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            description,
            triggeringEntry
        );
    }
}
