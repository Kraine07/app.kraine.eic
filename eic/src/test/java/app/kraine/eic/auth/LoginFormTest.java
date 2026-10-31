package app.kraine.eic.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import app.kraine.eic.form.LoginForm;

import static org.junit.jupiter.api.Assertions.*;

class LoginFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static LoginForm form(String email, String password) {
        LoginForm form = new LoginForm();
        form.setEmail(email);
        form.setPassword(password);
        return form;
    }

    @Test
    void acceptsEmailAndPassword() {
        assertTrue(validator.validate(form("ana@example.com", "anything")).isEmpty());
    }

    @Test
    void requiresBothFields() {
        assertEquals(2, validator.validate(form("", "")).size());
        assertEquals(2, validator.validate(form(null, null)).size());
    }

    @Test
    void rejectsAnInvalidEmail() {
        var violations = validator.validate(form("not-an-email", "secret"));

        assertEquals(1, violations.size());
        assertEquals("email", violations.iterator().next().getPropertyPath().toString());
    }
}
