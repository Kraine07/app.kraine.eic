package app.kraine.eic.validation;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import app.kraine.eic.form.ChangePasswordForm;


@Component
public class ChangePasswordValidator implements Validator {

    @Override
    public boolean supports(Class<?> type) {
        return ChangePasswordForm.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ChangePasswordForm form = (ChangePasswordForm) target;
        String currentPassword = form.getCurrentPassword();
        String newPassword = form.getNewPassword();
        String confirmPassword = form.getConfirmPassword();

        if (currentPassword == null || currentPassword.isEmpty()) {
            errors.rejectValue("currentPassword", "password.empty", "Current password cannot be empty.");
        }

        if (newPassword != null && !newPassword.isEmpty() && !AppPassword.isStrong(newPassword)) {
            errors.rejectValue("newPassword", "password.weak", "New password is not strong enough.");
        }

        if (confirmPassword != null && !confirmPassword.isEmpty() && !newPassword.equals(confirmPassword)) {
            errors.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match.");
        }

    }

}
