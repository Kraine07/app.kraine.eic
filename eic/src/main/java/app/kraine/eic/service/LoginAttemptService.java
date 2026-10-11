package app.kraine.eic.service;



import app.kraine.eic.model.User;
import app.kraine.eic.repository.UserRepoInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UA-13: suspends an account after 5 failed login attempts. It listens to the events Spring Security
 * publishes, so it covers the web login and the API's HTTP Basic login alike.
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepoInterface userRepository;

    @EventListener
    @Transactional
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        userRepository.findByEmailIgnoreCase(event.getAuthentication().getName().trim())
                .ifPresent(this::recordFailure);
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        userRepository.findByEmailIgnoreCase(event.getAuthentication().getName().trim()).ifPresent(user -> {
            if (user.getFailedAttempts() != 0) {
                user.setFailedAttempts(0);
                userRepository.save(user);
            }
        });
    }

    @Transactional(readOnly = true)
    public boolean isSuspended(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(user -> user.getIsSuspended())
                .orElse(false);
    }

    private void recordFailure(User user) {
        if (user.getIsSuspended() != true) {
            return; // already suspended: nothing more to count
        }
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            // Open decision: UA-13 can suspend the only active Administrator, which UA-23 forbids.

            //TODO : DECIDE WHAT TO DO WHEN THE ONLY ACTIVE ADMINISTRATOR IS SUSPENDED.  SHOULD WE PREVENT THIS OR ALLOW IT?  IF WE ALLOW IT, THEN THE SYSTEM WILL BE UNMANAGEABLE.  IF WE PREVENT IT, THEN THE SYSTEM WILL BE UNSECURE.  WHAT IS THE RIGHT BALANCE BETWEEN SECURITY AND MANAGEABILITY?
            user.setIsSuspended(true);
            log.warn("Account {} suspended after {} failed login attempts", user.getEmail(), attempts);
        }
        userRepository.save(user);
    }
}