package app.kraine.eic.auth;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import app.kraine.eic.exception.WrongCurrentPasswordException;
import app.kraine.eic.form.ChangePasswordForm;
import app.kraine.eic.validation.AppPassword;
import app.kraine.eic.validation.ChangePasswordValidator;

/**
 * The change-password page. Any signed-in user can open it; a user with a temporary password cannot
 * open anything else (see PasswordChangeRequiredFilter).
 */
@Controller
@RequestMapping("/change-password")
@RequiredArgsConstructor
public class PasswordChangeController {

    static final String VIEW = "change-password";
    static final String DONE_URL = "redirect:/dashboard";

    private final PasswordChangeService passwordChangeService;
    private final ChangePasswordValidator changePasswordValidator;
    private final AuthRefresher authRefresher;

    @InitBinder("changePasswordForm")
    void initBinder(WebDataBinder binder) {
        binder.addValidators(changePasswordValidator);
    }

    @GetMapping
    public String showForm(Authentication authentication, Model model) {
        model.addAttribute("changePasswordForm", new ChangePasswordForm());
        return view(model, authentication);
    }

    @PostMapping
    public String changePassword(@Valid @ModelAttribute("changePasswordForm") ChangePasswordForm form,
                                BindingResult result,
                                Authentication authentication,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                Model model,
                                RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                passwordChangeService.changePassword(authentication.getName(), form.getCurrentPassword(),
                        form.getNewPassword());
                authRefresher.refresh(request, response);
                redirect.addFlashAttribute("successMessage", "Your password has been changed.");
                return DONE_URL;
            } catch (WrongCurrentPasswordException ex) {
                result.rejectValue("currentPassword", "password.wrong", ex.getMessage());
            }
        }
        return view(model, authentication);
    }

    private String view(Model model, Authentication authentication) {
        model.addAttribute("required", PasswordChangeRequiredFilter.mustChangePassword(authentication));
        model.addAttribute("passwordRule", AppPassword.PASSWORD_RULE);
        return VIEW;
    }
}
