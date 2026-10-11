package app.kraine.eic;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.service.AppUserDetailsService;

import java.util.Optional;
 
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
 
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {
 
    @Mock private UserRepoInterface userRepository;
    @InjectMocks private AppUserDetailsService service;
 
    private static User user(UserStatus status) {
        User user = new User();
        user.setEmail("ana@example.com");
        user.setPassword("hashed");
        Role role = new Role();
        role.setRoleType(RoleType.EDITOR);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
 
    @Test
    void loadsAnActiveAccountWithItsRole() {
        when(userRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
 
        UserDetails details = service.loadUserByUsername(" ana@example.com ");
 
        assertEquals("ana@example.com", details.getUsername());
        assertEquals("hashed", details.getPassword());
        assertTrue(details.isAccountNonLocked());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EDITOR")));
    }
 
    @Test
    void marksASuspendedAccountAsLocked() {
        when(userRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user(UserStatus.SUSPENDED)));
 
        assertFalse(service.loadUserByUsername("ana@example.com").isAccountNonLocked());
    }
 
    @Test
    void rejectsAnUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());
 
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("nobody@example.com"));
    }
}
 
