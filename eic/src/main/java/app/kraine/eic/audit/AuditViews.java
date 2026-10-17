package app.kraine.eic.audit;


import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Read-only data for the audit page. Templates call the accessors, e.g. row.time(). */
public final class AuditViews {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private AuditViews() {
    }

    /** Times are shown, and exported, in UTC. */
    public static String formatTime(Instant instant) {
        return instant == null ? "" : TIME.format(instant);
    }

    public record AuditRow(Long id, String time, String type, String actor, String target, String ipAddress,
                           String details) {

        public static AuditRow from(AuditEvent event) {
            return new AuditRow(event.getId(), formatTime(event.getOccurredAt()), event.getEventType().getLabel(),
                    event.getActor(), event.getTarget(), event.getIpAddress(), event.getDetails());
        }
    }

    public record AuditPageView(List<AuditRow> rows, int page, int totalPages, long totalElements) {

        public boolean hasPrevious() {
            return page > 0;
        }

        public boolean hasNext() {
            return page + 1 < totalPages;
        }

        public int displayPage() {
            return page + 1;
        }
    }

    public record TypeOption(String value, String label) {
    }
}
