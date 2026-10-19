package app.kraine.eic;


import org.junit.jupiter.api.Test;

import app.kraine.eic.audit.AuditEventSpecifications;
import app.kraine.eic.audit.AuditFilter;

import java.time.Instant;
import java.time.LocalDate;
 
import static org.junit.jupiter.api.Assertions.assertEquals;
 
class AuditEventSpecificationsTest {
 
    @Test
    void searchesForPercentAndUnderscoreInsteadOfTreatingThemAsWildcards() {
        assertEquals("a\\%b\\_c\\\\d", AuditEventSpecifications.escapeLike("a%b_c\\d"));
    }
 
    @Test
    void theFilterCoversWholeDaysInUtc() {
        AuditFilter filter = new AuditFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 12), null, "");
 
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), filter.fromInstant());
        assertEquals(Instant.parse("2026-10-13T00:00:00Z"), filter.toInstantExclusive());
    }
}
