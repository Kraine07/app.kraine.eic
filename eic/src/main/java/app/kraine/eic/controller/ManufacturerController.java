package app.kraine.eic.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import app.kraine.eic.exception.DuplicateManufacturerNameException;
import app.kraine.eic.exception.ManufacturerInUseException;
import app.kraine.eic.form.ManufacturerForm;
import app.kraine.eic.service.ManufacturerService;
 
/** The 'Manufacturer management' screen and its forms (MAN-1 to MAN-4). Administrators only. */
@Controller
@RequestMapping("/manufacturers")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class ManufacturerController {
 
    static final String LIST_VIEW = "manufacturers/list";
    static final String FORM_VIEW = "manufacturers/form";
    static final String LIST_REDIRECT = "redirect:/manufacturers";
 
    private final ManufacturerService manufacturerService;
 
    @GetMapping
    public String list(Model model) {
        model.addAttribute("manufacturers", manufacturerService.list());
        return LIST_VIEW;
    }
 
    // ------------------------------------------------------------------ create (UC-08)
 
    @GetMapping("/new")
    public String newForm(Model model) {
        return form(model, new ManufacturerForm(), null);
    }
 
    @PostMapping
    public String create(@Valid @ModelAttribute("manufacturerForm") ManufacturerForm form,
                    BindingResult result,
                    Model model,
                    RedirectAttributes redirect,
                    Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                manufacturerService.createManufacturer(form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Manufacturer created.");
                return LIST_REDIRECT;
            } catch (DuplicateManufacturerNameException ex) {
                result.rejectValue("name", "duplicate", "This manufacturer name is already in use.");
            }
        }
        return form(model, form, null);
    }
 
    // ------------------------------------------------------------------ update (UC-09)
 
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        return form(model, manufacturerService.findForEdit(id).form(), id);
    }
 
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                    @Valid @ModelAttribute("manufacturerForm") ManufacturerForm form,
                    BindingResult result,
                    Model model,
                    RedirectAttributes redirect,
                    Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                manufacturerService.updateManufacturer(id, form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Manufacturer updated.");
                return LIST_REDIRECT;
            } catch (DuplicateManufacturerNameException ex) {
                result.rejectValue("name", "duplicate", "This manufacturer name is already in use.");
            }
        }
        return form(model, form, id);
    }
 
    // ------------------------------------------------------------------ delete (UC-10)
 
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect, Authentication authentication) {
        try {
            manufacturerService.deleteManufacturer(id, authentication.getName());
            redirect.addFlashAttribute("successMessage", "Manufacturer deleted.");
        } catch (ManufacturerInUseException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return LIST_REDIRECT;
    }
 
    // ------------------------------------------------------------------ view helper
 
    /** [id] is null when creating. */
    private String form(Model model, ManufacturerForm form, Long id) {
        model.addAttribute("manufacturerForm", form);
        model.addAttribute("editing", id != null);
        model.addAttribute("manufacturerId", id);
        return FORM_VIEW;
    }
}
 