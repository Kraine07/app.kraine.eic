package app.kraine.eic.userManagement;


import java.util.List;

import app.kraine.eic.form.UserForm;

/** Read-only data for the user management pages. Templates call the accessors, e.g. u.fullName(). */
public final class UserViews {

    private UserViews() {
    }

    public record UserRow(Long id, String fullName, String email, String roleLabel, boolean active) {
    }

    public record UserDashboard(List<UserRow> users, long totalUsers, long activeAdministrators, long suspendedUsers) {
    }

    public record UserEditData(Long id, UserForm form, String roleLabel, boolean suspended) {
    }

    public record RoleOption(String value, String label) {
    }
}