package app.kraine.eic.usermanagement;

import org.junit.jupiter.api.Test;

import app.kraine.eic.auth.TemporaryPasswordGenerator;

import static org.junit.jupiter.api.Assertions.*;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void alwaysMeetsThePasswordPolicy() {
        for (int i = 0; i < 200; i++) {
            String password = generator.generate();

            assertEquals(TemporaryPasswordGenerator.LENGTH, password.length());
            assertTrue(password.chars().anyMatch(Character::isUpperCase), password);
            assertTrue(password.chars().anyMatch(Character::isLowerCase), password);
            assertTrue(password.chars().anyMatch(Character::isDigit), password);
            assertTrue(password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)), password);
        }
    }

    @Test
    void differsFromOneCallToTheNext() {
        assertNotEquals(generator.generate(), generator.generate());
    }
}
