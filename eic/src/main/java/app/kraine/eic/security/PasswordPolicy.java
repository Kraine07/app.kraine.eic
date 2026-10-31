package app.kraine.eic.security;

/** UA-11: the one place that decides what a strong password is. */
public final class PasswordPolicy {

    public static final String RULE = "Password must be at least 8 characters and include an uppercase letter, "
            + "a lowercase letter, a number and a special character.";

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        return password != null
                && password.length() >= 8
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
    }
}