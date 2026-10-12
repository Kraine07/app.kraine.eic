package app.kraine.eic.validation;

public class AppPassword {

    public static final int MIN_LENGTH = 8;

    public static final String PASSWORD_RULE = "Password must be at least {" + MIN_LENGTH + "} characters and include an uppercase letter, a lowercase letter, a number and a special character.";

    public static final boolean isStrong(String password) {
        return password.length() >= 8
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
    }

}
