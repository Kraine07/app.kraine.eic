package app.kraine.eic.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import app.kraine.eic.form.UserForm;
import app.kraine.eic.service.UserService;
import app.kraine.eic.userManagement.UserViews;
import app.kraine.eic.userManagement.UserViews.UserDashboard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import app.kraine.eic.exception.DuplicateEmailException;
import app.kraine.eic.exception.EmailDeliveryException;
import app.kraine.eic.exception.LastAdministratorException;
import app.kraine.eic.exception.UserLinkedToPropertyException;

@Controller
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class UserController {

    static final String LIST_VIEW = "users/list";
    static final String FORM_VIEW = "users/form";
    static final String LIST_REDIRECT = "redirect:/users";

    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        UserViews.UserDashboard dashboard = userService.dashboard();
        model.addAttribute("dashboard", dashboard);
        return LIST_VIEW;
    }

    // ------------------------------------------------------------------ create (UC-02)

    @GetMapping("/new")
    public String newForm(Model model) {
        return createForm(model, new UserForm());
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("userForm") UserForm form,
                        BindingResult result,
                        Model model,
                        RedirectAttributes redirect,
                        Authentication authentication) {
        if (form.getRoleType() == null) {
            result.rejectValue("role", "required", "Select a role.");
        }
        if (!result.hasErrors()) {
            try {
                userService.createUser(form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "User account created. A temporary password was sent to "
                        + form.getEmail().trim() + ".");
                return LIST_REDIRECT;
            } catch (DuplicateEmailException ex) {
                result.rejectValue("email", "duplicate", "This email is already in use.");
            } catch (EmailDeliveryException ex) {
                model.addAttribute("errorMessage",
                        "The account was not created because the temporary password could not be emailed. " + ex.getMessage());
            }
        }
        return createForm(model, form);
    }

    // ------------------------------------------------------------------ update (UC-03)

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        return editForm(model, userService.findForEdit(id));
    }
 
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("userForm") UserForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirect,
                         Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                userService.updateUser(id, form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "User account updated.");
                return LIST_REDIRECT;
            } catch (DuplicateEmailException ex) {
                result.rejectValue("email", "duplicate", "This email is already in use.");
            }
        }
        UserViews.UserEditData data = userService.findForEdit(id);
        form.setRoleType(data.form().getRoleType()); // the role is not editable, keep what is stored
        return editForm(model, new UserViews.UserEditData(id, form, data.roleLabel(), data.suspended()));
    }
 
    // ------------------------------------------------------------------ suspend / activate / delete
 
    @PostMapping("/{id}/suspend")
    public String suspend(@PathVariable Long id, RedirectAttributes redirect, Authentication authentication) {
        try {
            userService.suspend(id, authentication.getName());
            redirect.addFlashAttribute("successMessage", "User account suspended.");
        } catch (LastAdministratorException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return LIST_REDIRECT;
    }
 
    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect, Authentication authentication) {
        try {
            userService.activate(id, authentication.getName());
            redirect.addFlashAttribute("successMessage",
                    "User account activated. A new temporary password was sent to the user's email.");
        } catch (EmailDeliveryException ex) {
            redirect.addFlashAttribute("errorMessage",
                    "The account was not activated because the temporary password could not be emailed. " + ex.getMessage());
        }
        return LIST_REDIRECT;
    }
 
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect, Authentication authentication) {
        try {
            userService.delete(id, authentication.getName());
            redirect.addFlashAttribute("successMessage", "User account deleted.");
        } catch (UserLinkedToPropertyException | LastAdministratorException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return LIST_REDIRECT;
    }
 
    // ------------------------------------------------------------------ view helpers
 
    private String createForm(Model model, UserForm form) {
        model.addAttribute("userForm", form);
        model.addAttribute("editing", false);
        model.addAttribute("roles", userService.roleOptions());
        return FORM_VIEW;
    }
 
    private String editForm(Model model, UserViews.UserEditData data) {
        model.addAttribute("userForm", data.form());
        model.addAttribute("editing", true);
        model.addAttribute("userId", data.id());
        model.addAttribute("roleLabel", data.roleLabel());
        model.addAttribute("suspended", data.suspended());
        return FORM_VIEW;
    }
}
