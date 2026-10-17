package app.kraine.eic.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import app.kraine.eic.exception.AuthenticationException;
import app.kraine.eic.exception.LoginAttemptsExhaustedException;
import app.kraine.eic.form.LoginForm;
import app.kraine.eic.service.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;



/** UC-01: the login page. */
@Slf4j
@RequestMapping("/login")
@Controller
@RequiredArgsConstructor
public class LoginController {

    static final String LOGIN_VIEW = "login";
    static final String SIGNED_IN_URL = "redirect:/dashboard";

    static final String BAD_CREDENTIALS = "Email or password is incorrect.";
    static final String SUSPENDED = "This account is suspended. Contact an Administrator.";
    static final String ATTEMPTS_EXHAUSTED =
            "Too many failed login attempts. Your account has been suspended. Contact an Administrator.";
    static final String FAILED = "Sign-in failed. Try again later.";
    static final String CHANGE_PASSWORD_URL = "redirect:/change-password";

    private final LoginService loginService;



    /** Steps 1-2: navigate to the login page; the system displays the form.
     * */
    @GetMapping
    public String showForm(Authentication authentication, @RequestParam(required = false) String timeout,
            @RequestParam(required = false) String logout, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return SIGNED_IN_URL;
        }
        model.addAttribute("loginForm", new LoginForm());
        if (logout != null) {
            model.addAttribute("infoMessage", "You have been signed out.");
        }
        if (timeout != null) {
            model.addAttribute("infoMessage", "Your session timed out.");
        }
        return LOGIN_VIEW;
    }





    /** Steps 4-12: 'Sign In' validates the form, then authenticates. */
    @PostMapping
    public String signIn(@Valid @ModelAttribute("loginForm") LoginForm form, BindingResult result, HttpServletRequest request, HttpServletResponse response, Model model) {
        if (result.hasErrors()) {
            return LOGIN_VIEW; // alternative flow: invalid fields are highlighted, the user corrects them
        }
        try {
            Authentication authUser = loginService.login(form, request, response);

            // if password is temporary, the user is redirected to the change-password page (UA-9).
            if (authUser instanceof UsernamePasswordAuthenticationToken) {
                return SIGNED_IN_URL;
            } else {
                return CHANGE_PASSWORD_URL;
            }

        } catch (LoginAttemptsExhaustedException ex) {
            return fail(model, ATTEMPTS_EXHAUSTED);
        } catch (LockedException | DisabledException ex) {
            return fail(model, SUSPENDED);
        } catch (BadCredentialsException ex) {
            return fail(model, BAD_CREDENTIALS);
        } catch (AuthenticationException ex) {
            return fail(model, FAILED);
        }
        // UC-01 step 10 (temporary password) belongs to the change-password use case (UA-9).
    }



    private String fail(Model model, String message) {
        model.addAttribute("errorMessage", message);
        return LOGIN_VIEW;
    }
}
