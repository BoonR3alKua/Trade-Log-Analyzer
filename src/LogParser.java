import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Returns null for malformed records; the monitor can continue with the next line. */
public class LogParser {
    private static final Pattern LINE = Pattern.compile(
            "\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\]\\s+(INFO|WARN|ERROR|FATAL)"
            + "\\s*-\\s*([A-Z][A-Z0-9_]*):\\s*(\\S.*)");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    public LogEntry parseLine(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        Matcher match = LINE.matcher(line);
        if (match.matches()) {
            try {
                return new LogEntry(LocalDateTime.parse(match.group(1), TIME),
                        match.group(2), match.group(3), match.group(4));
            } catch (DateTimeParseException e) {
                // An invalid calendar date is a malformed record too.
            }
        }
        System.err.println("Skipping malformed record: " + line);
        return null;
    }
}