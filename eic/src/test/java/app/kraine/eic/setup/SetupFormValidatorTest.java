package app.kraine.eic.setup;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import app.kraine.eic.form.SetupForm;
import app.kraine.eic.validation.SetupFormValidator;

import static org.junit.jupiter.api.Assertions.*;

class SetupFormValidatorTest {

    private final SetupFormValidator validator = new SetupFormValidator();

    private Errors validate(String password, String confirmation) {
        SetupForm form = new SetupForm();
        form.setPassword(password);
        form.setConfirmPassword(confirmation);
        Errors errors = new BeanPropertyBindingResult(form, "setupForm");
        validator.validate(form, errors);
        return errors;
    }

    @Test
    void acceptsAStrongMatchingPassword() {
        assertFalse(validate("Str0ng!Pass", "Str0ng!Pass").hasErrors());
    }

    @Test
    void rejectsWeakPasswords() {
        for (String weak : new String[]{"short1!A", "nouppercase1!", "NOLOWERCASE1!", "NoDigits!!", "NoSpecial123"}) {
            assertTrue(validate(weak, weak).hasFieldErrors("password"), weak);
        }
    }

    @Test
    void rejectsAConfirmationThatDoesNotMatch() {
        Errors errors = validate("Str0ng!Pass", "Str0ng!Pasz");

        assertTrue(errors.hasFieldErrors("confirmPassword"));
        assertFalse(errors.hasFieldErrors("password"));
    }

    @Test
    void leavesEmptyFieldsToTheRequiredChecks() {
        assertFalse(validate("", "").hasErrors());
        assertFalse(validate(null, null).hasErrors());
    }
}
