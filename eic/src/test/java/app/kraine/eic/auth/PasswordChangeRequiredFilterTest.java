package app.kraine.eic.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PasswordChangeRequiredFilterTest {

    private final PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter();
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(boolean temporaryPassword) {
        List<SimpleGrantedAuthority> authorities = temporaryPassword
                ? List.of(new SimpleGrantedAuthority("ROLE_EDITOR"),
                          new SimpleGrantedAuthority(PasswordChangeRequiredFilter.AUTHORITY))
                : List.of(new SimpleGrantedAuthority("ROLE_EDITOR"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("ana@example.com", null, authorities));
    }

    private void request(String method, String path) throws Exception {
        filter.doFilter(new MockHttpServletRequest(method, path), response, chain);
    }

    @Test
    void sendsEveryOtherPageToTheChangePasswordPage() throws Exception {
        signIn(true);

        request("GET", "/equipment");

        assertEquals("/change-password", response.getRedirectedUrl());
        assertNull(chain.getRequest(), "the request must not reach the application");
    }

    @Test
    void refusesApiCallsWith403() throws Exception {
        signIn(true);

        request("GET", "/api/equipment");

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Password change required"));
        assertNull(chain.getRequest());
    }

    @Test
    void stillAllowsTheChangePasswordPageSignOutAndStaticFiles() throws Exception {
        signIn(true);

        for (String path : new String[]{"/change-password", "/logout", "/error", "/css/app.css",
                "/api/me", "/api/account/password"}) {
            chain = new MockFilterChain();
            request("POST", path);
            assertNotNull(chain.getRequest(), path);
        }
    }

    @Test
    void doesNotRestrictAUserWhosePasswordIsNotTemporary() throws Exception {
        signIn(false);

        request("GET", "/users");

        assertNotNull(chain.getRequest());
        assertNull(response.getRedirectedUrl());
    }

    @Test
    void doesNotRestrictAnonymousVisitors() throws Exception {
        request("GET", "/login");

        assertNotNull(chain.getRequest());
    }

    @Test
    void doesNotBlockCorsPreflightRequests() throws Exception {
        signIn(true);

        request("OPTIONS", "/api/equipment");

        assertNotNull(chain.getRequest());
    }
}
