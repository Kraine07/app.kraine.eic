package app.kraine.eic.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row of the audit trail. Every column is updatable = false: once written, an event is never
 * changed, and the application has no code that deletes one except the retention purge.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "audit_event", indexes = {
        @Index(name = "idx_audit_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_actor", columnList = "actor")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /** Stored in UTC. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private AuditEventType eventType;

    /** Who did it: an email address, or "system". */
    @Column(name = "actor", updatable = false)
    private String actor;

    /** The account it affected. */
    @Column(name = "target", updatable = false)
    private String target;

    @Column(name = "ip_address", updatable = false, length = 64)
    private String ipAddress;

    /** A short note. Never contains a password. */
    @Column(name = "details", updatable = false, length = 500)
    private String details;
}
