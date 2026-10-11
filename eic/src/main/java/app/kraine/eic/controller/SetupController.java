package app.kraine.eic.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import app.kraine.eic.service.FirstRunService;

import app.kraine.eic.exception.SetupAlreadyCompletedException;
import app.kraine.eic.form.SetupForm;
import app.kraine.eic.validation.SetupFormValidator;

/** The 'Add User' form shown on first run (UC-33). It only exists while no user account does (UA-22). */
@Controller
@RequestMapping({"", "/"})
@RequiredArgsConstructor
public class SetupController {

    private final FirstRunService firstRunService;
    private final SetupFormValidator setupFormValidator;

    @InitBinder("setupForm")
    void initBinder(WebDataBinder binder) {
        binder.addValidators(setupFormValidator);
    }


    @GetMapping
    public String showForm(Model model) {
        // alternative flow: if users already exist, redirect to login page
        if (!firstRunService.isSetupRequired()) {
            return "login"; // alternative flow: users already exist
        }
        model.addAttribute("setupForm", new SetupForm());
        return "setup";
    }

    @PostMapping("/setup")
    public String createAdministrator(@Valid @ModelAttribute("setupForm") SetupForm form, BindingResult result, RedirectAttributes redirect) {
        if (!firstRunService.isSetupRequired()) {
            return "login";
        }
        if (result.hasErrors()) {
            return "setup"; // alternative flow: invalid fields are highlighted, the user corrects them
        }
        try {
            firstRunService.createFirstAdministrator(form);
        } catch (SetupAlreadyCompletedException ex) {
            return "login"; // someone else finished setup a moment earlier
        }
        redirect.addFlashAttribute("successMessage", "Administrator account created. Sign in to continue.");
        return "login";
    }
}