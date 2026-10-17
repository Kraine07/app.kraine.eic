package app.kraine.eic.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditEventRepoInterface
        extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {

    /** UA-26: the only way events are ever deleted. */
    @Modifying
    @Query("DELETE FROM AuditEvent e WHERE e.occurredAt < :cutoff")
    int purgeOlderThan(@Param("cutoff") Instant cutoff);

    long countByEventTypeAndOccurredAtAfter(AuditEventType eventType, Instant since);
}
