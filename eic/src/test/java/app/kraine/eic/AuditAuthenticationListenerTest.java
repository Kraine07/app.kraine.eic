package app.kraine.eic;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import app.kraine.eic.audit.AuditAuthenticationListener;
import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;

import java.util.List;
 
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
 
@ExtendWith(MockitoExtension.class)
class AuditAuthenticationListenerTest {
 
    private static final String EMAIL = "ana@example.com";
 
    @Mock private AuditService auditService;
    @InjectMocks private AuditAuthenticationListener listener;
 
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }
 
    private static void requestTo(String uri) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("POST", uri)));
    }
 
    private static AuthenticationSuccessEvent success() {
        return new AuthenticationSuccessEvent(UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of()));
    }
 
    @Test
    void recordsAWebLogin() {
        requestTo("/login");
 
        listener.onSuccess(success());
 
        verify(auditService).recordQuietly(AuditEventType.LOGIN_SUCCESS, EMAIL, EMAIL, null);
    }
 
    @Test
    void ignoresTheRepeatedSignInOfEveryOtherApiCall() {
        requestTo("/api/equipment");
 
        listener.onSuccess(success());
 
        verifyNoInteractions(auditService);
    }
 
    @Test
    void recordsTheOneApiCallTheAppMakesWhenSomeoneSignsIn() {
        requestTo("/api/me");
 
        listener.onSuccess(success());
 
        verify(auditService).recordQuietly(AuditEventType.LOGIN_SUCCESS, EMAIL, EMAIL, null);
    }
 
    @Test
    void recordsALoginOutsideAnyRequest() {
        listener.onSuccess(success());
 
        verify(auditService).recordQuietly(AuditEventType.LOGIN_SUCCESS, EMAIL, EMAIL, null);
    }
 
    @Test
    void recordsAFailedLogin() {
        listener.onBadCredentials(new AuthenticationFailureBadCredentialsEvent(
                new UsernamePasswordAuthenticationToken(EMAIL, "wrong"), new BadCredentialsException("bad")));
 
        verify(auditService).recordQuietly(AuditEventType.LOGIN_FAILED, EMAIL, EMAIL, "Bad credentials");
    }
 
    @Test
    void neverRecordsAPasswordTypedIntoTheEmailField() {
        listener.onBadCredentials(new AuthenticationFailureBadCredentialsEvent(
                new UsernamePasswordAuthenticationToken("MyS3cret#Password", "x"), new BadCredentialsException("bad")));
 
        verify(auditService).recordQuietly(AuditEventType.LOGIN_FAILED, "(not an email address)",
                "(not an email address)", "Bad credentials");
    }
 
    @Test
    void recordsALoginBlockedBecauseTheAccountIsSuspended() {
        listener.onLocked(new AuthenticationFailureLockedEvent(
                new UsernamePasswordAuthenticationToken(EMAIL, "x"), new LockedException("suspended")));
 
        verify(auditService).recordQuietly(AuditEventType.LOGIN_BLOCKED, EMAIL, EMAIL, "Account is suspended");
    }
}
 
