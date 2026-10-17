package app.kraine.eic;




import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import app.kraine.eic.controller.UserController;
import app.kraine.eic.exception.DuplicateEmailException;
import app.kraine.eic.exception.EmailDeliveryException;
import app.kraine.eic.exception.LastAdministratorException;
import app.kraine.eic.exception.UserLinkedToPropertyException;
import app.kraine.eic.form.UserForm;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.service.UserService;
import app.kraine.eic.userManagement.UserViews;

import java.util.List;
 
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
 
@ExtendWith(MockitoExtension.class)
class UserManagementControllerTest {
 
    private static final String ADMIN = "admin@example.com";
 
    @Mock private UserService userService;
    @Mock private AuditService auditService;
 
    private final Model model = new ConcurrentModel();
    private final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
    private final Authentication admin =
            UsernamePasswordAuthenticationToken.authenticated(ADMIN, null, List.of());
 
    private UserController controller() {
        return new UserController(userService, auditService);
    }
 
    private static UserForm form(RoleType role) {
        UserForm form = new UserForm();
        form.setFirstName("Ana");
        form.setLastName("Silva");
        form.setEmail("ana@example.com");
        form.setRoleType(role);
        return form;
    }
 
    private static BindingResult resultFor(UserForm form) {
        return new BeanPropertyBindingResult(form, "userForm");
    }
 
    // ------------------------------------------------------------------ create
 
    @Test
    void createRequiresARole() {
        UserForm form = form(null);
        BindingResult result = resultFor(form);
        when(userService.roleOptions()).thenReturn(List.of());
 
        String view = controller().create(form, result, model, redirect, admin);
 
        assertEquals("users/form", view);
        assertTrue(result.hasFieldErrors("roleType"));
        verify(userService, never()).createUser(any(), any());
    }
 
    @Test
    void createRedirectsToTheListWithAConfirmation() {
        UserForm form = form(RoleType.EDITOR);
 
        String view = controller().create(form, resultFor(form), model, redirect, admin);
 
        assertEquals("redirect:/users", view);
        assertTrue(redirect.getFlashAttributes().get("successMessage").toString().contains("ana@example.com"));
        verify(userService).createUser(form, ADMIN);
    }
 
    @Test
    void createShowsADuplicateEmailUnderTheEmailField() {
        UserForm form = form(RoleType.EDITOR);
        BindingResult result = resultFor(form);
        when(userService.roleOptions()).thenReturn(List.of());
        doThrow(new DuplicateEmailException("ana@example.com")).when(userService).createUser(any(), any());
 
        String view = controller().create(form, result, model, redirect, admin);
 
        assertEquals("users/form", view);
        assertTrue(result.hasFieldErrors("email"));
    }
 
    @Test
    void createExplainsAnEmailDeliveryFailure() {
        UserForm form = form(RoleType.EDITOR);
        when(userService.roleOptions()).thenReturn(List.of());
        doThrow(new EmailDeliveryException("The email could not be sent."))
                .when(userService).createUser(any(), any());
 
        String view = controller().create(form, resultFor(form), model, redirect, admin);
 
        assertEquals("users/form", view);
        assertTrue(model.getAttribute("errorMessage").toString().contains("not created"));
    }
 
    // ------------------------------------------------------------------ update
 
    @Test
    void updateShowsADuplicateEmailUnderTheEmailField() {
        UserForm form = form(null);
        BindingResult result = resultFor(form);
        doThrow(new DuplicateEmailException("ana@example.com")).when(userService).updateUser(any(), any(), any());
        when(userService.findForEdit(5L)).thenReturn(
                new UserViews.UserEditData(5L, form(RoleType.EDITOR), "Editor", false));
 
        String view = controller().update(5L, form, result, model, redirect, admin);
 
        assertEquals("users/form", view);
        assertTrue(result.hasFieldErrors("email"));
        assertEquals(Boolean.TRUE, model.getAttribute("editing"));
    }
 
    // ------------------------------------------------------------------ suspend / delete
 
    @Test
    void suspendExplainsWhyTheLastAdministratorCannotBeSuspended() {
        doThrow(new LastAdministratorException()).when(userService).suspend(1L, ADMIN);
 
        String view = controller().suspend(1L, redirect, admin);
 
        assertEquals("redirect:/users", view);
        assertEquals("At least one active Administrator account must exist.",
                redirect.getFlashAttributes().get("errorMessage"));
    }
 
    @Test
    void deleteExplainsWhyAUserLinkedToAPropertyCannotBeDeleted() {
        doThrow(new UserLinkedToPropertyException("Test user linked to property.")).when(userService).delete(4L, ADMIN);
 
        controller().delete(4L, redirect, admin);
 
        assertTrue(redirect.getFlashAttributes().get("errorMessage").toString().contains("linked to a property"));
    }
 
    @Test
    void deleteConfirmsOnSuccess() {
        controller().delete(4L, redirect, admin);
 
        assertEquals("User account deleted.", redirect.getFlashAttributes().get("successMessage"));
    }
 
    // ------------------------------------------------------------------ dashboard
 
    @Test
    void listShowsTheDashboardAndTheFailedLoginCount() {
        when(userService.dashboard()).thenReturn(new UserViews.UserDashboard(List.of(), 0, 0, 0));
        when(auditService.countFailedLoginsSince(any())).thenReturn(3L);
 
        String view = controller().list(model);
 
        assertEquals("users/list", view);
        assertEquals(3L, model.getAttribute("failedLogins24h"));
    }
}
 
