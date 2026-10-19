package app.kraine.eic.audit;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import app.kraine.eic.audit.AuditViews.AuditPageView;
import app.kraine.eic.audit.AuditViews.AuditRow;
import app.kraine.eic.audit.AuditViews.TypeOption;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/** UA-27 and UA-28: the read-only audit log, for Administrators. There is no way to change an event here. */
@Controller
@RequestMapping("/audit")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class AuditController {

    public static final String VIEW = "audit";
    public static final int PAGE_SIZE = 25;
    public static final int EXPORT_LIMIT = 10_000;
    public static final int DEFAULT_DAYS = 30;
    public static final String INVALID_RANGE = "The 'from' date must not be after the 'to' date.";

    private final AuditService auditService;



    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                    @RequestParam(required = false) AuditEventType type,
                    @RequestParam(required = false) String email,
                    @RequestParam(defaultValue = "0") int page,
                    Model model) {
        AuditFilter filter = filterOf(from, to, type, email);

        model.addAttribute("from", filter.from());
        model.addAttribute("to", filter.to());
        model.addAttribute("typeValue", type == null ? "" : type.name());
        model.addAttribute("email", filter.email());
        model.addAttribute("types", typeOptions());
        model.addAttribute("retentionDays", auditService.retentionDays());

        if (filter.from().isAfter(filter.to())) {
            model.addAttribute("errorMessage", INVALID_RANGE);
            model.addAttribute("results", new AuditPageView(List.of(), 0, 0, 0));
            return VIEW;
        }

        Page<AuditEvent> events = auditService.search(filter, Math.max(page, 0), PAGE_SIZE);
        List<AuditRow> rows = events.getContent().stream().map(AuditRow::from).toList();
        model.addAttribute("results",
                new AuditPageView(rows, events.getNumber(), events.getTotalPages(), events.getTotalElements()));
        return VIEW;
    }




    /** The events that match the current filters, newest first, as a CSV download (at most EXPORT_LIMIT). */
    @GetMapping("/export.csv")
    public void export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                    @RequestParam(required = false) AuditEventType type,
                    @RequestParam(required = false) String email,
                    HttpServletResponse response) throws IOException {
        AuditFilter filter = filterOf(from, to, type, email);
        if (filter.from().isAfter(filter.to())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, INVALID_RANGE);
            return;
        }
        List<AuditEvent> events = auditService.findForExport(filter, EXPORT_LIMIT);

        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"audit-log-" + LocalDate.now(ZoneOffset.UTC) + ".csv\"");
        AuditCsv.write(response.getWriter(), events);
    }




    /** Dates left empty default to the last 30 days. */
    private static AuditFilter filterOf(LocalDate from, LocalDate to, AuditEventType type, String email) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_DAYS);
        return new AuditFilter(effectiveFrom, effectiveTo, type, email == null ? "" : email.trim());
    }



    private static List<TypeOption> typeOptions() {
        return Arrays.stream(AuditEventType.values()).map(t -> new TypeOption(t.name(), t.getLabel())).toList();
    }
}
