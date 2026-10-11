package app.kraine.eic.validation;


import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import app.kraine.eic.form.SetupForm;

/** Password strength (UA-11) and confirmation check. Runs after the annotation-based checks. */
@Component
public class SetupFormValidator implements Validator {

    static final String PASSWORD_RULE = "Password must be at least 8 characters and include an uppercase letter, "
            + "a lowercase letter, a number and a special character.";

    @Override
    public boolean supports(Class<?> type) {
        return SetupForm.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        SetupForm form = (SetupForm) target;
        String password = form.getPassword();
        String confirmation = form.getConfirmPassword();

        if (password != null && !password.isEmpty() && !isStrong(password)) {
            errors.rejectValue("password", "password.weak", PASSWORD_RULE);
        }
        if (password != null && confirmation != null && !confirmation.isEmpty() && !password.equals(confirmation)) {
            errors.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match.");
        }
    }

    static boolean isStrong(String password) {
        return password.length() >= 8
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
    }
}
