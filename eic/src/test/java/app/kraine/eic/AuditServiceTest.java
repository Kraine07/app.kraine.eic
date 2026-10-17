package app.kraine.eic;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
 
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
 
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
 
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
 
    @Mock private AuditEventRepoInterface repository;
 
    private AuditService service;
 
    @BeforeEach
    void setUp() {
        service = new AuditService(repository, mock(PlatformTransactionManager.class));
    }
 
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }
 
    private AuditEvent savedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
 
    // ------------------------------------------------------------------ writing
 
    @Test
    void recordsAnEventWithTheIpAddressOfTheCurrentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Instant before = Instant.now();
 
        service.record(AuditEventType.USER_CREATED, "admin@example.com", "ana@example.com", "Role EDITOR");
 
        AuditEvent event = savedEvent();
        assertEquals(AuditEventType.USER_CREATED, event.getEventType());
        assertEquals("admin@example.com", event.getActor());
        assertEquals("ana@example.com", event.getTarget());
        assertEquals("Role EDITOR", event.getDetails());
        assertEquals("203.0.113.5", event.getIpAddress());
        assertFalse(event.getOccurredAt().isBefore(before));
    }
 
    @Test
    void hasNoIpAddressOutsideARequest() {
        service.record(AuditEventType.LOGOUT, "ana@example.com", "ana@example.com", null);
 
        assertNull(savedEvent().getIpAddress());
    }
 
    @Test
    void cutsOverlongTextToTheColumnSizes() {
        service.record(AuditEventType.LOGIN_FAILED, "a".repeat(400), null, "x".repeat(600));
 
        AuditEvent event = savedEvent();
        assertEquals(AuditService.MAX_FIELD, event.getActor().length());
        assertEquals(AuditService.MAX_DETAILS, event.getDetails().length());
    }
 
    @Test
    void recordPassesStorageFailuresOnSoTheAuditedChangeFailsToo() {
        when(repository.save(any(AuditEvent.class))).thenThrow(new IllegalStateException("db down"));
 
        assertThrows(IllegalStateException.class,
                () -> service.record(AuditEventType.USER_DELETED, "admin@example.com", "ana@example.com", null));
    }
 
    @Test
    void recordQuietlySavesTheEvent() {
        service.recordQuietly(AuditEventType.LOGIN_SUCCESS, "ana@example.com", "ana@example.com", null);
 
        assertEquals(AuditEventType.LOGIN_SUCCESS, savedEvent().getEventType());
    }
 
    @Test
    void recordQuietlyNeverThrowsSoALoginIsNotBlocked() {
        when(repository.save(any(AuditEvent.class))).thenThrow(new IllegalStateException("db down"));
 
        assertDoesNotThrow(() ->
                service.recordQuietly(AuditEventType.LOGIN_FAILED, "ana@example.com", "ana@example.com", null));
    }
 
    // ------------------------------------------------------------------ identifiers
 
    @Test
    void keepsAnEmailAddressAsIs() {
        assertEquals("ana@example.com", AuditService.safeIdentifier(" ana@example.com "));
    }
 
    @Test
    void neverStoresSomethingThatIsNotAnEmailAddress() {
        assertEquals("(not an email address)", AuditService.safeIdentifier("MyS3cret#Password"));
        assertEquals("(not an email address)", AuditService.safeIdentifier("a@b".repeat(200)));
        assertNull(AuditService.safeIdentifier(null));
    }
 
    // ------------------------------------------------------------------ retention
 
    private static void assertCutoffIsAboutDaysAgo(Instant cutoff, long days) {
        Instant expected = Instant.now().minus(days, ChronoUnit.DAYS);
        assertTrue(Duration.between(cutoff, expected).abs().toMinutes() < 1, cutoff + " vs " + expected);
    }
 
    @Test
    void purgesEventsOlderThanOneYear() {
        when(repository.purgeOlderThan(any())).thenReturn(7);
 
        int deleted = service.purgeExpired();
 
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).purgeOlderThan(cutoff.capture());
        assertEquals(7, deleted);
        assertCutoffIsAboutDaysAgo(cutoff.getValue(), 365);
    }
 
    @Test
    void neverKeepsEventsForLessThanAYearEvenIfConfiguredToDoSo() {
        ReflectionTestUtils.setField(service, "retentionDays", 30);
 
        service.purgeExpired();
 
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).purgeOlderThan(cutoff.capture());
        assertEquals(365, service.retentionDays());
        assertCutoffIsAboutDaysAgo(cutoff.getValue(), 365);
    }
 
    @Test
    void keepsEventsLongerWhenConfigured() {
        ReflectionTestUtils.setField(service, "retentionDays", 730);
 
        service.purgeExpired();
 
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).purgeOlderThan(cutoff.capture());
        assertEquals(730, service.retentionDays());
        assertCutoffIsAboutDaysAgo(cutoff.getValue(), 730);
    }
 
    // ------------------------------------------------------------------ monitoring
 
    @Test
    void countsFailedLoginsSinceAGivenTime() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        when(repository.countByEventTypeAndOccurredAtAfter(eq(AuditEventType.LOGIN_FAILED), eq(since))).thenReturn(4L);
 
        assertEquals(4L, service.countFailedLoginsSince(since));
    }
}
 
