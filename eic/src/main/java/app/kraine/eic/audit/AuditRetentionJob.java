package app.kraine.eic.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** UA-26: removes expired audit events once a night (03:30 by default). */
@Component
@RequiredArgsConstructor
public class AuditRetentionJob {

    private final AuditService auditService;

    @Scheduled(cron = "${app.audit.purge-cron:0 30 3 * * *}")
    public void purge() {
        auditService.purgeExpired();
    }
}
