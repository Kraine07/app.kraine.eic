package app.kraine.eic.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import app.kraine.eic.exception.LoginAttemptsExhaustedException;
import app.kraine.eic.form.LoginForm;
import app.kraine.eic.service.LoginAttemptService;
import app.kraine.eic.service.LoginService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private LoginAttemptService loginAttemptService;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private LoginService service() {
        return new LoginService(authenticationManager, loginAttemptService);
    }

    private static LoginForm form() {
        LoginForm form = new LoginForm();
        form.setEmail(" ana@example.com ");
        form.setPassword("Str0ng!Pass");
        return form;
    }

    @Test
    void signsTheUserInAndStoresTheContextInTheSession() {
        Authentication authenticated =
                UsernamePasswordAuthenticationToken.authenticated("ana@example.com", null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);

        service().login(form(), request, response);

        assertSame(authenticated, SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(request.getSession(false).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test
    void changesAnExistingSessionIdAfterLogin() {
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        String before = session.getId();
        when(authenticationManager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated("ana@example.com", null, List.of()));

        service().login(form(), request, response);

        assertNotEquals(before, request.getSession(false).getId());
    }

    @Test
    void rethrowsBadCredentialsWhileTheAccountIsStillActive() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(loginAttemptService.isSuspended("ana@example.com")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> service().login(form(), request, response));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void reportsExhaustedAttemptsWhenTheFailureSuspendedTheAccount() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(loginAttemptService.isSuspended("ana@example.com")).thenReturn(true);

        assertThrows(LoginAttemptsExhaustedException.class, () -> service().login(form(), request, response));
    }

    @Test
    void passesThroughAnAlreadySuspendedAccount() {
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("locked"));

        assertThrows(LockedException.class, () -> service().login(form(), request, response));
    }
}
