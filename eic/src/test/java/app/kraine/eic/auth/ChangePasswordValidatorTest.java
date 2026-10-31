package app.kraine.eic.auth;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import app.kraine.eic.form.ChangePasswordForm;
import app.kraine.eic.validation.ChangePasswordValidator;

import static org.junit.jupiter.api.Assertions.*;

class ChangePasswordValidatorTest {

    private final ChangePasswordValidator validator = new ChangePasswordValidator();

    private Errors validate(String current, String next, String confirmation) {
        ChangePasswordForm form = new ChangePasswordForm();
        form.setCurrentPassword(current);
        form.setNewPassword(next);
        form.setConfirmPassword(confirmation);
        Errors errors = new BeanPropertyBindingResult(form, "changePasswordForm");
        validator.validate(form, errors);
        return errors;
    }

    @Test
    void acceptsAStrongNewPasswordThatMatches() {
        assertFalse(validate("Temp#Pass12", "N3w!Passw0rd", "N3w!Passw0rd").hasErrors());
    }

    @Test
    void rejectsAWeakNewPassword() {
        assertTrue(validate("Temp#Pass12", "weak", "weak").hasFieldErrors("newPassword"));
    }

    @Test
    void rejectsKeepingTheTemporaryPassword() {
        Errors errors = validate("Temp#Pass12", "Temp#Pass12", "Temp#Pass12");

        assertTrue(errors.hasFieldErrors("newPassword"));
        assertEquals(ChangePasswordValidator.SAME_AS_CURRENT, errors.getFieldError("newPassword").getDefaultMessage());
    }

    @Test
    void rejectsAConfirmationThatDoesNotMatch() {
        Errors errors = validate("Temp#Pass12", "N3w!Passw0rd", "N3w!Passw0rX");

        assertTrue(errors.hasFieldErrors("confirmPassword"));
        assertFalse(errors.hasFieldErrors("newPassword"));
    }

    @Test
    void leavesEmptyFieldsToTheRequiredChecks() {
        assertFalse(validate("", "", "").hasErrors());
        assertFalse(validate(null, null, null).hasErrors());
    }
}
