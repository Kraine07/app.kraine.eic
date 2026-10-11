package app.kraine.eic.service;

import app.kraine.eic.model.LoginForm;
import app.kraine.eic.exception.LoginAttemptsExhaustedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;



/** UC-01 steps 6-13: authenticates the credentials and starts the signed-in session. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * @throws LoginAttemptsExhaustedException this failed attempt was the last one allowed; the account is now suspended
     * @throws org.springframework.security.authentication.LockedException the account was already suspended
     * @throws BadCredentialsException unknown email or wrong password (deliberately not told apart)
     */
    public Authentication login(LoginForm form, HttpServletRequest request, HttpServletResponse response) {
        String email = form.getEmail().trim();

        Authentication authentication;
        try {
            // Checks the suspended flag, then the password. Spring Security publishes the success or
            // failure event that LoginAttemptService counts.
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, form.getPassword()));
        } catch (BadCredentialsException ex) {
            if (loginAttemptService.isSuspended(email)) {
                log.warn("Login failed for {}: attempts exhausted, account suspended", email);
                throw new LoginAttemptsExhaustedException(ex);
            }
            log.warn("Login failed for {}: bad credentials", email);
            throw ex;
        } catch (AuthenticationException ex) {
            log.warn("Login refused for {}: {}", email, ex.getClass().getSimpleName());
            throw ex;
        }

        // A new session id after login, so an id planted before login is useless (session fixation).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        log.info("Login succeeded for {}", email);
        return authentication;
    }
}
