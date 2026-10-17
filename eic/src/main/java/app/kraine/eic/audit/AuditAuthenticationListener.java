package app.kraine.eic.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records every login attempt from the events Spring Security publishes, so the web login and the
 * API's HTTP Basic login are covered by one piece of code and nothing is counted twice.
 */
@Component
@RequiredArgsConstructor
public class AuditAuthenticationListener {

    private final AuditService auditService;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        // The API signs in again on every request (HTTP Basic). Only /api/me, which the app calls once
        // when a person signs in, is recorded; every other API call would flood the log.
        String path = currentPath();
        if (path != null && path.startsWith("/api/") && !path.equals("/api/me")) {
            return;
        }
        String email = event.getAuthentication().getName();
        auditService.recordQuietly(AuditEventType.LOGIN_SUCCESS, email, email, null);
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String attempted = AuditService.safeIdentifier(event.getAuthentication().getName());
        auditService.recordQuietly(AuditEventType.LOGIN_FAILED, attempted, attempted, "Bad credentials");
    }

    @EventListener
    public void onLocked(AuthenticationFailureLockedEvent event) {
        String attempted = AuditService.safeIdentifier(event.getAuthentication().getName());
        auditService.recordQuietly(AuditEventType.LOGIN_BLOCKED, attempted, attempted, "Account is suspended");
    }

    private static String currentPath() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getRequestURI().substring(servlet.getRequest().getContextPath().length());
        }
        return null;
    }

    @EventListener
    public void onLogout(org.springframework.security.authentication.event.LogoutSuccessEvent event) {
        String email = event.getAuthentication().getName();
        auditService.recordQuietly(AuditEventType.LOGOUT, email, email, null);
    }

}
