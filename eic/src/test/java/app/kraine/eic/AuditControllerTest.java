package app.kraine.eic;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import app.kraine.eic.audit.AuditController;
import app.kraine.eic.audit.AuditEvent;
import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditFilter;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.audit.AuditViews.AuditPageView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
 
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
 
@ExtendWith(MockitoExtension.class)
class AuditControllerTest {
 
    @Mock private AuditService auditService;
 
    private final Model model = new ConcurrentModel();
 
    @BeforeEach
    void setUp() {
        lenient().when(auditService.retentionDays()).thenReturn(365);
    }
 
    private AuditController controller() {
        return new AuditController(auditService);
    }
 
    private static AuditEvent event() {
        AuditEvent event = new AuditEvent();
        event.setId(1L);
        event.setOccurredAt(Instant.parse("2026-10-12T10:15:30Z"));
        event.setEventType(AuditEventType.LOGIN_FAILED);
        event.setActor("ana@example.com");
        event.setTarget("ana@example.com");
        event.setIpAddress("203.0.113.5");
        event.setDetails("Bad credentials");
        return event;
    }
 
    private void givenResults(List<AuditEvent> events) {
        when(auditService.search(any(AuditFilter.class), anyInt(), eq(AuditController.PAGE_SIZE)))
                .thenReturn(new PageImpl<>(events, PageRequest.of(0, AuditController.PAGE_SIZE), events.size()));
    }
 
    // ------------------------------------------------------------------ list
 
    @Test
    void showsTheLastThirtyDaysWhenNoDatesAreGiven() {
        givenResults(List.of());
 
        String view = controller().list(null, null, null, null, 0, model);
 
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertEquals("audit/list", view);
        assertEquals(today, model.getAttribute("to"));
        assertEquals(today.minusDays(30), model.getAttribute("from"));
        assertEquals(365, model.getAttribute("retentionDays"));
    }
 
    @Test
    void passesTheFiltersToTheSearch() {
        givenResults(List.of());
 
        controller().list(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 12),
                AuditEventType.LOGIN_FAILED, "  ana@example.com ", 0, model);
 
        ArgumentCaptor<AuditFilter> filter = ArgumentCaptor.forClass(AuditFilter.class);
        verify(auditService).search(filter.capture(), eq(0), eq(AuditController.PAGE_SIZE));
        assertEquals(AuditEventType.LOGIN_FAILED, filter.getValue().type());
        assertEquals("ana@example.com", filter.getValue().email());
        assertEquals(LocalDate.of(2026, 10, 1), filter.getValue().from());
    }
 
    @Test
    void showsTheMatchingEventsAsRows() {
        givenResults(List.of(event()));
 
        controller().list(null, null, null, null, 0, model);
 
        AuditPageView results = (AuditPageView) model.getAttribute("results");
        assertEquals(1, results.rows().size());
        assertEquals("Login failed", results.rows().get(0).type());
        assertEquals("2026-10-12 10:15:30", results.rows().get(0).time());
        assertEquals(1, results.totalElements());
    }
 
    @Test
    void rejectsAReversedDateRangeWithoutSearching() {
        controller().list(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 1), null, null, 0, model);
 
        assertEquals(AuditController.INVALID_RANGE, model.getAttribute("errorMessage"));
        verify(auditService, never()).search(any(), anyInt(), anyInt());
    }
 
    @Test
    void treatsANegativePageAsTheFirstPage() {
        givenResults(List.of());
 
        controller().list(null, null, null, null, -5, model);
 
        verify(auditService).search(any(AuditFilter.class), eq(0), eq(AuditController.PAGE_SIZE));
    }
 
    // ------------------------------------------------------------------ export
 
    @Test
    void exportsTheMatchingEventsAsACsvDownload() throws Exception {
        when(auditService.findForExport(any(AuditFilter.class), eq(AuditController.EXPORT_LIMIT)))
                .thenReturn(List.of(event()));
        MockHttpServletResponse response = new MockHttpServletResponse();
 
        controller().export(null, null, null, null, response);
 
        assertTrue(response.getContentType().startsWith("text/csv"));
        assertTrue(response.getHeader("Content-Disposition").contains("attachment; filename=\"audit-log-"));
        String body = response.getContentAsString();
        assertTrue(body.startsWith("Time (UTC),Event,User,Account,IP address,Details"));
        assertTrue(body.contains("\"Login failed\",\"ana@example.com\""));
    }
 
    @Test
    void refusesToExportAReversedDateRange() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
 
        controller().export(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 1), null, null, response);
 
        assertEquals(400, response.getStatus());
        verify(auditService, never()).findForExport(any(), anyInt());
    }
}
