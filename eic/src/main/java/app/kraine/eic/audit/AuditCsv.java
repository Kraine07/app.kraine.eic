package app.kraine.eic.audit;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** UA-28: the audit export. Every value is quoted, and values that a spreadsheet would run as a formula are defused. */
public final class AuditCsv {

    static final String HEADER = "Time (UTC),Event,User,Account,IP address,Details";

    private AuditCsv() {
    }

    public static void write(Writer writer, List<AuditEvent> events) throws IOException {
        writer.write(HEADER);
        writer.write("\r\n");
        for (AuditEvent event : events) {
            writer.write(String.join(",",
                    cell(AuditViews.formatTime(event.getOccurredAt())),
                    cell(event.getEventType().getLabel()),
                    cell(event.getActor()),
                    cell(event.getTarget()),
                    cell(event.getIpAddress()),
                    cell(event.getDetails())));
            writer.write("\r\n");
        }
        writer.flush();
    }

    public static String cell(String value) {
        String text = value == null ? "" : value;
        // A cell that starts with = + - @ is run as a formula by Excel and others (CSV injection).
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
