package app.kraine.eic.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import app.kraine.eic.auth.PasswordChangeRequiredFilter;


/**
 * Security for the Thymeleaf pages. The login itself is done by LoginController, so formLogin() is
 * not used. If you already have a chain for the pages, merge these rules into it instead.
 * (@EnableMethodSecurity makes the @PreAuthorize rules work; remove it if it is enabled elsewhere.)
 */
@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {

    @Bean
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/login", "/setup", "/css/**", "/js/**", "/images/**", "/webjars/**", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                // Anonymous visitors are sent to the login page rather than getting a bare 403.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .logout(logout -> logout
                        .logoutUrl("/logout") // POST, with the CSRF token
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .sessionManagement(session -> session
                    .invalidSessionUrl("/login?timeout")   // redirect when session expires
                    .maximumSessions(1)                    // optional: limit concurrent sessions
                )   .addFilterAfter(new PasswordChangeRequiredFilter(), SecurityContextHolderFilter.class);
        return http.build();
    }



    /** The manager built from AppUserDetailsService and the PasswordEncoder bean. */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    

}