package app.kraine.eic.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The optional filters of the audit page, built as a query that only includes what was filled in. */
public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    public static Specification<AuditEvent> matching(AuditFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), filter.fromInstant()));
            predicates.add(builder.lessThan(root.<Instant>get("occurredAt"), filter.toInstantExclusive()));
            if (filter.type() != null) {
                predicates.add(builder.equal(root.get("eventType"), filter.type()));
            }
            if (StringUtils.hasText(filter.email())) {
                String pattern = "%" + escapeLike(filter.email().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.<String>get("actor")), pattern, '\\'),
                        builder.like(builder.lower(root.<String>get("target")), pattern, '\\')));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** So that % and _ typed by the Administrator are searched for, not treated as wildcards. */
    public static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
