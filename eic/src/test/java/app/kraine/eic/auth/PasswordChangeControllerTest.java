package app.kraine.eic.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import app.kraine.eic.exception.WrongCurrentPasswordException;
import app.kraine.eic.form.ChangePasswordForm;
import app.kraine.eic.security.PasswordPolicy;
import app.kraine.eic.validation.ChangePasswordValidator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordChangeControllerTest {

    @Mock private PasswordChangeService passwordChangeService;
    @Mock private AuthRefresher authenticationRefresher;

    private final Model model = new ConcurrentModel();
    private final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private final Authentication temporaryUser = UsernamePasswordAuthenticationToken.authenticated(
            "ana@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_EDITOR"),
                    new SimpleGrantedAuthority(PasswordChangeRequiredFilter.AUTHORITY)));

    private PasswordChangeController controller() {
        return new PasswordChangeController(passwordChangeService, new ChangePasswordValidator(), authenticationRefresher);
    }

    private static ChangePasswordForm form() {
        ChangePasswordForm form = new ChangePasswordForm();
        form.setCurrentPassword("Temp#Pass12");
        form.setNewPassword("N3w!Passw0rd");
        form.setConfirmPassword("N3w!Passw0rd");
        return form;
    }

    private static BindingResult resultFor(ChangePasswordForm form) {
        return new BeanPropertyBindingResult(form, "changePasswordForm");
    }

    @Test
    void theFormTellsATemporaryPasswordUserThatTheChangeIsRequired() {
        String view = controller().showForm(temporaryUser, model);

        assertEquals("change-password", view);
        assertEquals(Boolean.TRUE, model.getAttribute("required"));
        assertEquals(PasswordPolicy.RULE, model.getAttribute("passwordRule"));
    }

    @Test
    void changesThePasswordRefreshesTheSessionAndContinues() {
        ChangePasswordForm form = form();

        String view = controller().changePassword(form, resultFor(form), temporaryUser, request, response, model, redirect);

        assertEquals("redirect:/equipment", view);
        verify(passwordChangeService).changePassword("ana@example.com", "Temp#Pass12", "N3w!Passw0rd");
        verify(authenticationRefresher).refresh(request, response);
        assertEquals("Your password has been changed.", redirect.getFlashAttributes().get("successMessage"));
    }

    @Test
    void showsAWrongCurrentPasswordUnderThatField() {
        ChangePasswordForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new WrongCurrentPasswordException()).when(passwordChangeService).changePassword(any(), any(), any());

        String view = controller().changePassword(form, result, temporaryUser, request, response, model, redirect);

        assertEquals("change-password", view);
        assertTrue(result.hasFieldErrors("currentPassword"));
        assertEquals(Boolean.TRUE, model.getAttribute("required"));
        verify(authenticationRefresher, never()).refresh(any(), any());
    }

    @Test
    void doesNothingWhenTheFormIsInvalid() {
        ChangePasswordForm form = form();
        BindingResult result = resultFor(form);
        result.rejectValue("newPassword", "password.weak", "too weak");

        String view = controller().changePassword(form, result, temporaryUser, request, response, model, redirect);

        assertEquals("change-password", view);
        verify(passwordChangeService, never()).changePassword(any(), any(), any());
    }
}
