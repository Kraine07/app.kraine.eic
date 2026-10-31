package app.kraine.eic.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import app.kraine.eic.controller.LoginController;
import app.kraine.eic.exception.LoginAttemptsExhaustedException;
import app.kraine.eic.form.LoginForm;
import app.kraine.eic.service.LoginService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

    @Mock private LoginService loginService;

    private final Model model = new ConcurrentModel();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private LoginController controller() {
        return new LoginController(loginService);
    }

    private static LoginForm form() {
        LoginForm form = new LoginForm();
        form.setEmail("ana@example.com");
        form.setPassword("secret");
        return form;
    }

    private String submit(LoginForm form, BindingResult result) {
        return controller().signIn(form, result, request, response, model);
    }

    @Test
    void showsTheFormToAnonymousVisitors() {
        assertEquals("login", controller().showForm(null, null,null, model));
        assertTrue(model.containsAttribute("loginForm"));
    }

    @Test
    void sendsAlreadySignedInUsersToTheApp() {
        Authentication signedIn = UsernamePasswordAuthenticationToken.authenticated("ana@example.com", null, List.of());

        assertEquals("redirect:/equipment", controller().showForm(signedIn, null,null, model));
    }

    @Test
    void doesNotAuthenticateWhenTheFormIsInvalid() {
        LoginForm form = form();
        BindingResult errors = new BeanPropertyBindingResult(form, "loginForm");
        errors.rejectValue("email", "invalid", "Enter a valid email address.");

        assertEquals("login", submit(form, errors));
        verifyNoInteractions(loginService);
    }

    @Test
    void redirectsToTheAppOnSuccess() {
        LoginForm form = form();

        assertEquals("redirect:/equipment", submit(form, new BeanPropertyBindingResult(form, "loginForm")));
    }

    @Test
    void showsTheCredentialsMessageWithoutSayingWhichPartWasWrong() {
        LoginForm form = form();
        doThrow(new BadCredentialsException("bad")).when(loginService).login(any(), any(), any());

        assertEquals("login", submit(form, new BeanPropertyBindingResult(form, "loginForm")));
        assertEquals(LoginController.BAD_CREDENTIALS, model.getAttribute("errorMessage"));
    }

    @Test
    void showsTheSuspendedMessageForASuspendedAccount() {
        LoginForm form = form();
        doThrow(new LockedException("locked")).when(loginService).login(any(), any(), any());

        submit(form, new BeanPropertyBindingResult(form, "loginForm"));

        assertEquals(LoginController.SUSPENDED, model.getAttribute("errorMessage"));
    }

    @Test
    void showsTheExhaustedMessageWhenTheLastAttemptSuspendedTheAccount() {
        LoginForm form = form();
        doThrow(new LoginAttemptsExhaustedException(null)).when(loginService).login(any(), any(), any());

        submit(form, new BeanPropertyBindingResult(form, "loginForm"));

        assertEquals(LoginController.ATTEMPTS_EXHAUSTED, model.getAttribute("errorMessage"));
    }

    @Test
    void sendsAUserWithATemporaryPasswordToTheChangePasswordPage() {
        LoginForm form = form();
        when(loginService.login(any(), any(), any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated("ana@example.com", null,
                        List.of(new SimpleGrantedAuthority(PasswordChangeRequiredFilter.AUTHORITY))));

        assertEquals("redirect:/change-password", submit(form, new BeanPropertyBindingResult(form, "loginForm")));
    }

    @Test
    void sendsAUserWithANormalPasswordToTheApp() {
        LoginForm form = form();
        when(loginService.login(any(), any(), any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated("ana@example.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_EDITOR"))));

        assertEquals("redirect:/equipment", submit(form, new BeanPropertyBindingResult(form, "loginForm")));
    }
}
