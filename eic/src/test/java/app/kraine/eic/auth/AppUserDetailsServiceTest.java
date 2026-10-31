package app.kraine.eic.auth;

import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.service.AppUserDetailsService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock private UserRepoInterface userRepository;
    @InjectMocks private AppUserDetailsService service;

    private static User user(boolean suspended, boolean temporaryPassword) {
        Role role = new Role();
        role.setRoleType(RoleType.EDITOR);
        User user = new User();
        user.setEmail("ana@example.com");
        user.setPassword("hashed");
        user.setRole(role);
        user.setIsSuspended(suspended);
        user.setIsTemporaryPassword(temporaryPassword);
        return user;
    }

    private static List<String> authoritiesOf(UserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void loadsAnActiveAccountWithItsRole() {
        when(userRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user(false, false)));

        UserDetails details = service.loadUserByUsername(" ana@example.com ");

        assertEquals("ana@example.com", details.getUsername());
        assertEquals("hashed", details.getPassword());
        assertTrue(details.isAccountNonLocked());
        assertEquals(List.of("ROLE_EDITOR"), authoritiesOf(details));
    }

    @Test
    void marksASuspendedAccountAsLocked() {
        when(userRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user(true, false)));

        assertFalse(service.loadUserByUsername("ana@example.com").isAccountNonLocked());
    }

    @Test
    void addsThePasswordChangeAuthorityWhileThePasswordIsTemporary() {
        when(userRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user(false, true)));

        UserDetails details = service.loadUserByUsername("ana@example.com");

        assertTrue(authoritiesOf(details).contains(PasswordChangeRequiredFilter.AUTHORITY));
        assertTrue(authoritiesOf(details).contains("ROLE_EDITOR"));
    }

    @Test
    void rejectsAnUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("nobody@example.com"));
    }
}
