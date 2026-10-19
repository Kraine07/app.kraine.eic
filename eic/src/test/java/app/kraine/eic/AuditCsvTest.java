package app.kraine.eic;


import org.junit.jupiter.api.Test;

import app.kraine.eic.audit.AuditCsv;
import app.kraine.eic.audit.AuditEvent;
import app.kraine.eic.audit.AuditEventType;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
 
import static org.junit.jupiter.api.Assertions.assertEquals;
 
class AuditCsvTest {
 
    private static AuditEvent event(String details) {
        AuditEvent event = new AuditEvent();
        event.setOccurredAt(Instant.parse("2026-10-12T10:15:30Z"));
        event.setEventType(AuditEventType.LOGIN_FAILED);
        event.setActor("ana@example.com");
        event.setTarget("ana@example.com");
        event.setIpAddress("203.0.113.5");
        event.setDetails(details);
        return event;
    }
 
    private static String csv(AuditEvent... events) throws Exception {
        StringWriter writer = new StringWriter();
        AuditCsv.write(writer, List.of(events));
        return writer.toString();
    }
 
    @Test
    void writesAHeaderAndOneQuotedRowPerEvent() throws Exception {
        String[] lines = csv(event("Bad credentials")).split("\r\n");
 
        assertEquals("Time (UTC),Event,User,Account,IP address,Details", lines[0]);
        assertEquals("\"2026-10-12 10:15:30\",\"Login failed\",\"ana@example.com\",\"ana@example.com\","
                + "\"203.0.113.5\",\"Bad credentials\"", lines[1]);
        assertEquals(2, lines.length);
    }
 
    @Test
    void escapesQuotesAndCommas() {
        assertEquals("\"he said \"\"hi\"\", ok\"", AuditCsv.cell("he said \"hi\", ok"));
    }
 
    @Test
    void defusesValuesAThatSpreadsheetWouldRunAsAFormula() {
        assertEquals("\"'=HYPERLINK(\"\"http://evil\"\")\"", AuditCsv.cell("=HYPERLINK(\"http://evil\")"));
        assertEquals("\"'+1\"", AuditCsv.cell("+1"));
        assertEquals("\"'-1\"", AuditCsv.cell("-1"));
        assertEquals("\"'@SUM(A1)\"", AuditCsv.cell("@SUM(A1)"));
    }
 
    @Test
    void writesMissingValuesAsEmptyCells() {
        assertEquals("\"\"", AuditCsv.cell(null));
    }
}
 