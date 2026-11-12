package app.kraine.eic.audit;

import app.kraine.eic.exception.InvalidAuditRangeException;
import app.kraine.eic.model.AuditEvent;
import app.kraine.eic.audit.AuditViews.AuditRow;
import app.kraine.eic.audit.AuditViews.TypeOption;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * JSON version of the audit log page (UA-27, UA-28, UC-35), for the Flutter app. Administrators only, and read
 * only: there is no way to change or delete an event here. It uses the same filter rules as the web page: dates
 * left out default to the last 30 days, the email is matched as typed, and times are in UTC. The CSV export stays
 * on the web page (/audit/export.csv).
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class AuditApiController {

    private final AuditService auditService;

    /** The event types for the filter, and how long events are kept. */
    public record AuditOptionsResponse(List<TypeOption> types, int retentionDays, int defaultDays) {
    }

    /** One page of events, newest first. [from] and [to] are the dates actually used (ISO, UTC). */
    public record AuditLogResponse(List<AuditRow> events, int page, int totalPages, long totalElements,
                                   String from, String to) {
    }

    @GetMapping("/options")
    public AuditOptionsResponse options() {
        return new AuditOptionsResponse(AuditController.typeOptions(), auditService.retentionDays(),
                AuditController.DEFAULT_DAYS);
    }

    @GetMapping
    public AuditLogResponse list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(required = false) AuditEventType type,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(defaultValue = "0") int page) {
        AuditFilter filter = AuditController.filterOf(from, to, type, email);
        if (filter.from().isAfter(filter.to())) {
            throw new InvalidAuditRangeException(AuditController.INVALID_RANGE);
        }
        Page<AuditEvent> events = auditService.search(filter, Math.max(page, 0), AuditController.PAGE_SIZE);
        List<AuditRow> rows = events.getContent().stream().map(AuditRow::from).toList();
        return new AuditLogResponse(rows, events.getNumber(), events.getTotalPages(), events.getTotalElements(),
                filter.from().toString(), filter.to().toString());
    }
}
