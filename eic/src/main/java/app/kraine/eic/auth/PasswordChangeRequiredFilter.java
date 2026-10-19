package app.kraine.eic.auth;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
 
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
 
/**
 * UA-9: while a user's password is temporary, the only things they can reach are the change-password
 * page (or API endpoint), sign out, and the files those pages need. Everything else sends pages to
 * /change-password and answers API calls with 403.
 *
 * <p>The state travels in the user's authorities ({@link #AUTHORITY}, added by AppUserDetailsService),
 * so no database query is needed per request. Add it to both security chains, after the point where
 * the user has been authenticated.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {
 
    /** Present on the authentication of every user whose password is still temporary. */
    public static final String AUTHORITY = "PASSWORD_CHANGE_REQUIRED";
 
    public static final String CHANGE_PASSWORD_PATH = "/change-password";
 
    private static final Set<String> ALLOWED_PATHS = Set.of(
            CHANGE_PASSWORD_PATH, "/logout", "/error", "/favicon.ico", "/api/me", "/api/account/password");
    private static final List<String> ALLOWED_PREFIXES = List.of("/css/", "/js/", "/images/", "/webjars/");
 
    /** True for a signed-in user who still has to replace a temporary password. */
    public static boolean mustChangePassword(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream().anyMatch(a -> AUTHORITY.equals(a.getAuthority()));
    }
 
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
 
        // CORS preflight requests carry no credentials and must not be blocked.
        if (!mustChangePassword(authentication) || "OPTIONS".equalsIgnoreCase(request.getMethod()) || isAllowed(path)) {
            chain.doFilter(request, response);
            return;
        }
 
        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"status\":403,\"title\":\"Password change required\","
                    + "\"detail\":\"You must change your temporary password before using the application.\"}");
        } else {
            response.sendRedirect(request.getContextPath() + CHANGE_PASSWORD_PATH);
        }
    }
 
    private static boolean isAllowed(String path) {
        return ALLOWED_PATHS.contains(path) || ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
 