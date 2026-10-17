package app.kraine.eic.service;

import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import app.kraine.eic.auth.PasswordChangeRequiredFilter;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.userManagement.CustomUserDetails;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;






/**
 * Loads accounts for Spring Security, for both the web login and the API's HTTP Basic login.
 *
 * <p>Needs this method on your repository:
 * {@code Optional<User> findByEmailIgnoreCase(String email);}
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepoInterface userRepository;

    @Override
    @Transactional(readOnly = true)
public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = userRepository.findByEmailIgnoreCase(email.trim())
            .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));

    return new CustomUserDetails(user);
}

}