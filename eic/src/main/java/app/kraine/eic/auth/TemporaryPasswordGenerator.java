package app.kraine.eic.auth;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

/** Random temporary passwords that meet the password policy (UA-11). Look-alike characters are left out. */
@Component
public class TemporaryPasswordGenerator {

    static final int LENGTH = 12;

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%&*?";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        List<Character> characters = new ArrayList<>();
        characters.add(pick(UPPER));
        characters.add(pick(LOWER));
        characters.add(pick(DIGITS));
        characters.add(pick(SPECIAL));
        while (characters.size() < LENGTH) {
            characters.add(pick(ALL));
        }
        Collections.shuffle(characters, random);

        StringBuilder password = new StringBuilder(LENGTH);
        characters.forEach(password::append);
        return password.toString();
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
