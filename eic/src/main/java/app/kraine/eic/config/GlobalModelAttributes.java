package app.kraine.eic.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import app.kraine.eic.userManagement.CustomUserDetails;


/**
 *
 * GlobalModelAttributes
 * Allows varialbes to be accessible globally
 */


@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    @ModelAttribute
    public void addAuthUserToModel(Model model) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {

            // Add authUser to the model for every MVC controller annotated with @Controller. It does not apply to @RestController endpoints.
            model.addAttribute("authUser", userDetails);
        }
    }
}