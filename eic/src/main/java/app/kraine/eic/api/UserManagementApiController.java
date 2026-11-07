package app.kraine.eic.api;

import app.kraine.eic.audit.AuditService;
import app.kraine.eic.exception.FieldValidationException;
import app.kraine.eic.form.UserForm;
import app.kraine.eic.model.User;
import app.kraine.eic.service.UserService;
import app.kraine.eic.userManagement.UserViews.RoleOption;
import app.kraine.eic.userManagement.UserViews.UserDashboard;
import app.kraine.eic.userManagement.UserViews.UserEditData;
import app.kraine.eic.userManagement.UserViews.UserRow;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * JSON version of the user management pages (UC-02 to UC-06), for the Flutter app. Administrators only, and
 * every call goes through the same UserManagementService as the web pages: the same checks (duplicate email,
 * last active Administrator, user linked to a property), the same audit entries, the same emails.
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class UserManagementApiController {

    private final UserService userService;
    private final AuditService auditService;

    /** Everything the list screen shows. [roles] feeds the role drop-down of the New User form. */
    public record UsersResponse(List<UserRow> users, long totalUsers, long activeAdministrators,
                                long suspendedUsers, long failedLogins24h, List<RoleOption> roles) {
    }

    public record UserDetailResponse(Long id, String firstName, String lastName, String email, String roleLabel,
                                     boolean suspended) {
    }

    public record CreatedUserResponse(Long id, String email) {
    }

    @GetMapping
    public UsersResponse list() {
        UserDashboard dashboard = userService.dashboard();
        long failedLogins = auditService.countFailedLoginsSince(Instant.now().minus(24, ChronoUnit.HOURS));
        return new UsersResponse(dashboard.users(), dashboard.totalUsers(), dashboard.activeAdministrators(),
                dashboard.suspendedUsers(), failedLogins, userService.roleOptions());
    }

    @GetMapping("/{id}")
    public UserDetailResponse get(@PathVariable Long id) {
        UserEditData data = userService.findForEdit(id);
        UserForm form = data.form();
        return new UserDetailResponse(data.id(), form.getFirstName(), form.getLastName(), form.getEmail(),
                data.roleLabel(), data.suspended());
    }

    /** UC-02: 201 when created (the temporary password is emailed). */
    @PostMapping
    public ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody UserForm form, Authentication authentication) {
        if (form.getRoleType() == null) {
            throw new FieldValidationException("roleType", "Select a role.");
        }
        User created = userService.createUser(form, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedUserResponse(created.getId(), created.getEmail()));
    }

    /** UC-03: names and email. The role is not editable, so any roleType in the body is ignored. */
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @Valid @RequestBody UserForm form,
                                       Authentication authentication) {
        userService.updateUser(id, form, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** UC-04. */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable Long id, Authentication authentication) {
        userService.suspend(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** UC-05: enables the account and emails a new temporary password. */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id, Authentication authentication) {
        userService.activate(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** UC-06. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        userService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
