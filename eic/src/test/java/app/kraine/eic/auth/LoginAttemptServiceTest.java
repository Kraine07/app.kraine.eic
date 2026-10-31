package app.kraine.eic.auth;

import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.service.LoginAttemptService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final String EMAIL = "ana@example.com";

    @Mock private UserRepoInterface userRepository;
    @Mock private AuditService auditService;
    @InjectMocks private LoginAttemptService service;

    private User givenUser(boolean suspended, int failedAttempts) {
        User user = new User();
        user.setEmail(EMAIL);
        user.setIsSuspended(suspended);
        user.setFailedAttempts(failedAttempts);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        return user;
    }

    private static AuthenticationFailureBadCredentialsEvent failure() {
        return new AuthenticationFailureBadCredentialsEvent(
                new UsernamePasswordAuthenticationToken(EMAIL, "wrong"), new BadCredentialsException("bad"));
    }

    @Test
    void countsAFailedAttemptWithoutSuspendingBeforeTheLimit() {
        User user = givenUser(false, 3);

        service.onBadCredentials(failure());

        assertEquals(4, user.getFailedAttempts());
        assertFalse(user.getIsSuspended());
        verify(auditService, never()).recordQuietly(any(), any(), any(), any());
    }

    @Test
    void suspendsTheAccountOnTheFifthFailedAttempt() {
        User user = givenUser(false, 4);

        service.onBadCredentials(failure());

        assertEquals(5, user.getFailedAttempts());
        assertTrue(user.getIsSuspended());
        verify(userRepository).save(user);
        verify(auditService).recordQuietly(AuditEventType.ACCOUNT_LOCKED_OUT, "system", EMAIL, "5 failed login attempts");
    }

    @Test
    void doesNotCountAttemptsOnAnAlreadySuspendedAccount() {
        User user = givenUser(true, 5);

        service.onBadCredentials(failure());

        assertEquals(5, user.getFailedAttempts());
        verify(userRepository, never()).save(any());
    }

    @Test
    void ignoresAnUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        service.onBadCredentials(failure());

        verify(userRepository, never()).save(any());
    }

    @Test
    void aSuccessfulLoginResetsTheCounter() {
        User user = givenUser(false, 3);

        service.onSuccess(new AuthenticationSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of())));

        assertEquals(0, user.getFailedAttempts());
        verify(userRepository).save(user);
    }

    @Test
    void reportsWhetherAnAccountIsSuspended() {
        givenUser(true, 5);

        assertTrue(service.isSuspended(EMAIL));
    }
}
