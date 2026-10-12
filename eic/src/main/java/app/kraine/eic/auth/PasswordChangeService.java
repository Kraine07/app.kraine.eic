package app.kraine.eic.auth;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.kraine.eic.exception.WrongCurrentPasswordException;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.UserRepoInterface;
 
/** UA-9: replaces a user's password. For a temporary password this also lifts the restriction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordChangeService {
 
    private final UserRepoInterface userRepository;
    private final PasswordEncoder passwordEncoder;
 
    /** @throws WrongCurrentPasswordException if [currentPassword] does not match the stored password */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
 
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new WrongCurrentPasswordException();
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setIsTemporaryPassword(false);
        userRepository.save(user);
 
        log.info("Password changed for {}", user.getEmail());
    }
}