package app.kraine.eic.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** What the Administrator asked for on the audit page. Dates are whole days in UTC. */
public record AuditFilter(LocalDate from, LocalDate to, AuditEventType type, String email) {

    public Instant fromInstant() {
        return from.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** The start of the day after [to], so the whole of [to] is included. */
    public Instant toInstantExclusive() {
        return to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
