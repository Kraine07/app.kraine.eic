package app.kraine.eic.auth;

import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.exception.WrongCurrentPasswordException;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.UserRepoInterface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

    @Mock private UserRepoInterface userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @InjectMocks private PasswordChangeService service;

    private User givenUserWithTemporaryPassword() {
        User user = new User();
        user.setEmail("ana@example.com");
        user.setPassword("tempHash");
        user.setIsTemporaryPassword(true);
        when(userRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void storesTheNewPasswordHashedAndLiftsTheRestriction() {
        User user = givenUserWithTemporaryPassword();
        when(passwordEncoder.matches("Temp#Pass12", "tempHash")).thenReturn(true);
        when(passwordEncoder.encode("N3w!Passw0rd")).thenReturn("newHash");

        service.changePassword(" ana@example.com ", "Temp#Pass12", "N3w!Passw0rd");

        assertEquals("newHash", user.getPassword());
        assertFalse(user.getIsTemporaryPassword());
        verify(userRepository).save(user);
        verify(auditService).record(AuditEventType.PASSWORD_CHANGED, "ana@example.com", "ana@example.com", null);
    }

    @Test
    void refusesWhenTheCurrentPasswordIsWrong() {
        User user = givenUserWithTemporaryPassword();
        when(passwordEncoder.matches("wrong", "tempHash")).thenReturn(false);

        assertThrows(WrongCurrentPasswordException.class,
                () -> service.changePassword("ana@example.com", "wrong", "N3w!Passw0rd"));

        assertEquals("tempHash", user.getPassword());
        assertTrue(user.getIsTemporaryPassword());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownAccount() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> service.changePassword("nobody@example.com", "x", "y"));
    }
}
