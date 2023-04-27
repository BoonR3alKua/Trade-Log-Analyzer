import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses log entries using regular expressions.
 * Expected log format: [YYYY-MM-DD HH:mm:ss] LOG_LEVEL - ERROR_CODE: message
 */
public class LogParser {
    // Regex pattern to match log lines
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "\\[(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2})\\]\\s+(INFO|WARN|ERROR|FATAL)\\s*-\\s*([A-Z_]+):\\s*(.+)"
    );

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Parses a single log line and returns a LogEntry.
     * Returns null if the line doesn't match the expected format.
     */
    public LogEntry parseLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        try {
            Matcher matcher = LOG_PATTERN.matcher(line);
            
            if (!matcher.find()) {
                System.err.println("Warning: Could not parse line: " + line);
                return null;
            }

            String timestampStr = matcher.group(1);
            String logLevel = matcher.group(2);
            String errorCode = matcher.group(3);
            String message = matcher.group(4);

            LocalDateTime timestamp = LocalDateTime.parse(timestampStr, TIMESTAMP_FORMATTER);
            
            return new LogEntry(timestamp, logLevel, errorCode, message);

        } catch (DateTimeParseException e) {
            System.err.println("Error parsing timestamp in line: " + line);
            return null;
        } catch (Exception e) {
            System.err.println("Unexpected error parsing line: " + line);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Checks if a line contains a specific error code
     */
    public boolean containsErrorCode(String line, String errorCode) {
        if (line == null) return false;
        return line.contains(errorCode);
    }

    /**
     * Extracts all error codes from a line (if it matches the pattern)
     */
    public String extractErrorCode(String line) {
        LogEntry entry = parseLine(line);
        return entry != null ? entry.getErrorCode() : null;
    }
}
