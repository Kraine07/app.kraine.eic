package app.kraine.eic.controller;


import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import app.kraine.eic.auth.PasswordChangeRequiredFilter;

import java.util.List;

/** Lets the Flutter app check a username/password and learn the account's roles and password state. */
@RestController
public class MeApiController {

    /** passwordChangeRequired: true while the password is temporary; the app must show its change-password screen. */
    public record Me(String username, List<String> roles, boolean passwordChangeRequired) {
    }

    @GetMapping("/api/me")
    public Me me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !PasswordChangeRequiredFilter.AUTHORITY.equals(authority))
                .toList();
        return new Me(authentication.getName(), roles, PasswordChangeRequiredFilter.mustChangePassword(authentication));
    }
}
