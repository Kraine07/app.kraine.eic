package app.kraine.eic.service;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import app.kraine.eic.repository.UserRepoInterface;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepoInterface userRepository;


    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        app.kraine.eic.model.User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));

        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword())
                // ADMINISTRATOR / EDITOR become ROLE_ADMINISTRATOR / ROLE_EDITOR (UC-01 step 11)
                .roles(user.getRole().getRoleType().name())
                // UC-01 step 6: a suspended account is refused before the password is even checked
                .accountLocked(user.getIsSuspended())
                .build();
    }
}