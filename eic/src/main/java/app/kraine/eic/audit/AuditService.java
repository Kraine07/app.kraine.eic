package app.kraine.eic.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

/**
 * UA-18: writes, searches and purges the audit trail.
 *
 * <p>There are two ways to write an event, because the two situations need opposite things:
 * <ul>
 *   <li>{@link #record}: joins the caller's transaction. Used for changes to accounts, so a change
 *       and its audit event are saved together or not at all.</li>
 *   <li>{@link #recordQuietly}: its own transaction, and it never throws. Used for logins, where a
 *       failure to write the log must not stop anyone signing in.</li>
 * </ul>
 */
@Slf4j
@Service
public class AuditService {

    static final int MAX_FIELD = 255;
    static final int MAX_DETAILS = 500;
    /** UA-26: events are kept for at least a year. */
    static final int MIN_RETENTION_DAYS = 365;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final AuditEventRepoInterface repository;
    private final TransactionTemplate separateTransaction;

    @Value("${app.audit.retention-days:365}")
    private int retentionDays = MIN_RETENTION_DAYS;

    public AuditService(AuditEventRepoInterface repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.separateTransaction = new TransactionTemplate(transactionManager);
        this.separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------ write

    /** Part of the caller's transaction. If this fails, the change being audited fails too. */
    public void record(AuditEventType type, String actor, String target, String details) {
        repository.save(newEvent(type, actor, target, details));
    }

    /** Own transaction; a storage problem is logged and swallowed. */
    public void recordQuietly(AuditEventType type, String actor, String target, String details) {
        try {
            AuditEvent event = newEvent(type, actor, target, details);
            separateTransaction.executeWithoutResult(status -> repository.save(event));
        } catch (RuntimeException ex) {
            log.error("Could not write audit event {} for {}", type, actor, ex);
        }
    }

    /**
     * People sometimes type their password into the email field. Anything that does not look like an
     * email address is replaced, so such a password never reaches the audit trail.
     */
    public static String safeIdentifier(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.length() <= MAX_FIELD && EMAIL.matcher(value).matches() ? value : "(not an email address)";
    }

    private AuditEvent newEvent(AuditEventType type, String actor, String target, String details) {
        AuditEvent event = new AuditEvent();
        event.setOccurredAt(Instant.now());
        event.setEventType(type);
        event.setActor(limit(actor, MAX_FIELD));
        event.setTarget(limit(target, MAX_FIELD));
        event.setIpAddress(currentIpAddress());
        event.setDetails(limit(details, MAX_DETAILS));
        return event;
    }

    /** The address of the request being handled, if there is one. Behind a proxy see server.forward-headers-strategy. */
    private static String currentIpAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getRemoteAddr();
        }
        return null;
    }

    private static String limit(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(AuditFilter filter, int page, int size) {
        return repository.findAll(AuditEventSpecifications.matching(filter), PageRequest.of(page, size, NEWEST_FIRST));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> findForExport(AuditFilter filter, int limit) {
        return repository.findAll(AuditEventSpecifications.matching(filter), PageRequest.of(0, limit, NEWEST_FIRST))
                .getContent();
    }

    /** UA-29: the counter on the user management page. */
    @Transactional(readOnly = true)
    public long countFailedLoginsSince(Instant since) {
        return repository.countByEventTypeAndOccurredAtAfter(AuditEventType.LOGIN_FAILED, since);
    }

    // ------------------------------------------------------------------ retention

    /** How long events are kept, never less than the year UA-26 requires. */
    public int retentionDays() {
        return Math.max(retentionDays, MIN_RETENTION_DAYS);
    }

    /** UA-26: deletes events older than the retention period. */
    @Transactional
    public int purgeExpired() {
        if (retentionDays < MIN_RETENTION_DAYS) {
            log.warn("app.audit.retention-days={} is below the one year UA-26 requires; using {} days",
                    retentionDays, MIN_RETENTION_DAYS);
        }
        Instant cutoff = Instant.now().minus(retentionDays(), ChronoUnit.DAYS);
        int deleted = repository.purgeOlderThan(cutoff);
        log.info("Audit purge removed {} events older than {}", deleted, cutoff);
        return deleted;
    }
}
