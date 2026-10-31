package app.kraine.eic.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationRefresherTest {

    @Mock private UserDetailsService userDetailsService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dropsThePasswordChangeRestrictionFromTheSignedInSession() {
        UserDetails fresh = org.springframework.security.core.userdetails.User.withUsername("ana@example.com")
                .password("newHash").authorities("ROLE_EDITOR").build();
        when(userDetailsService.loadUserByUsername("ana@example.com")).thenReturn(fresh);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "ana@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_EDITOR"),
                        new SimpleGrantedAuthority(PasswordChangeRequiredFilter.AUTHORITY))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        String sessionBefore = request.getSession().getId();

        new AuthRefresher(userDetailsService).refresh(request, new MockHttpServletResponse());

        Authentication after = SecurityContextHolder.getContext().getAuthentication();
        assertFalse(PasswordChangeRequiredFilter.mustChangePassword(after));
        assertTrue(after.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
                .contains("ROLE_EDITOR"));
        assertNotEquals(sessionBefore, request.getSession(false).getId());
        assertNotNull(request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }
}
