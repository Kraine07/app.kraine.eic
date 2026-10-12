package app.kraine.eic.auth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
/**
 * Rebuilds the signed-in user's authentication from the database. After a temporary password has been
 * changed this drops PASSWORD_CHANGE_REQUIRED from the session, so the user can carry on without
 * signing in again.
 */
@Component
@RequiredArgsConstructor
public class AuthRefresher {
    private final UserDetailsService userDetailsService;

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = securityContextHolderStrategy.getContext().getAuthentication();
        UserDetails details = userDetailsService.loadUserByUsername(current.getName());
        Authentication refreshed =
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());

        // A new session id after a change of credentials, as after login.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(refreshed);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
