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
 
        List<GrantedAuthority> authorities = new ArrayList<>();
        // ADMINISTRATOR / EDITOR become ROLE_ADMINISTRATOR / ROLE_EDITOR (UC-01 step 11)
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleType().name()));
        if (Boolean.TRUE.equals(user.getIsTemporaryPassword())) {
            // UA-9: until the password is changed, PasswordChangeRequiredFilter lets this user reach
            // the change-password page and nothing else.
            authorities.add(new SimpleGrantedAuthority(PasswordChangeRequiredFilter.AUTHORITY));
        }
 
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(authorities)
                // UC-01 step 6: a suspended account is refused before the password is even checked
                .accountLocked(Boolean.TRUE.equals(user.getIsSuspended()))
                .build();
    }
}
 
// @Service
// @RequiredArgsConstructor
// public class AppUserDetailsService implements UserDetailsService {

//     private final UserRepoInterface userRepository;


//     @Override
//     @Transactional(readOnly = true)
//     public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
//         app.kraine.eic.model.User user = userRepository.findByEmailIgnoreCase(email.trim())
//                 .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));

//         return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
//                 .password(user.getPassword())
//                 // ADMINISTRATOR / EDITOR become ROLE_ADMINISTRATOR / ROLE_EDITOR (UC-01 step 11)
//                 .roles(user.getRole().getRoleType().name())
//                 // UC-01 step 6: a suspended account is refused before the password is even checked
//                 .accountLocked(user.getIsSuspended())
//                 .build();
//     }
// }