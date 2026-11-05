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

import app.kraine.eic.exception.DuplicateRegionNameException;
import app.kraine.eic.exception.RegionInUseException;
import app.kraine.eic.form.RegionForm;
import app.kraine.eic.service.RegionService;
 
/** The 'Region management' screen and its forms (RM-1 to RM-4). Administrators only. */
@Controller
@RequestMapping("/regions")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class RegionController {
 
    static final String LIST_VIEW = "regions/list";
    static final String FORM_VIEW = "regions/form";
    static final String LIST_REDIRECT = "redirect:/regions";
 
    private final RegionService regionService;
 
    @GetMapping
    public String list(Model model) {
        model.addAttribute("regions", regionService.list());
        return LIST_VIEW;
    }
 
    // ------------------------------------------------------------------ create (UC-14)
 
    @GetMapping("/new")
    public String newForm(Model model) {
        return form(model, new RegionForm(), null);
    }
 
    @PostMapping
    public String create(@Valid @ModelAttribute("regionForm") RegionForm form,
                        BindingResult result,
                        Model model,
                        RedirectAttributes redirect,
                        Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                regionService.createRegion(form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Region created.");
                return LIST_REDIRECT;
            } catch (DuplicateRegionNameException ex) {
                result.rejectValue("name", "duplicate", "This region name is already in use.");
            }
        }
        return form(model, form, null);
    }
 
    // ------------------------------------------------------------------ update (UC-15)
 
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        return form(model, regionService.findForEdit(id).form(), id);
    }
 
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                        @Valid @ModelAttribute("regionForm") RegionForm form,
                        BindingResult result,
                        Model model,
                        RedirectAttributes redirect,
                        Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                regionService.updateRegion(id, form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Region updated.");
                return LIST_REDIRECT;
            } catch (DuplicateRegionNameException ex) {
                result.rejectValue("name", "duplicate", "This region name is already in use.");
            }
        }
        return form(model, form, id);
    }
 
    // ------------------------------------------------------------------ delete (UC-16)
 
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect, Authentication authentication) {
        try {
            regionService.deleteRegion(id, authentication.getName());
            redirect.addFlashAttribute("successMessage", "Region deleted.");
        } catch (RegionInUseException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return LIST_REDIRECT;
    }
 
    // ------------------------------------------------------------------ view helper
 
    /** [id] is null when creating. */
    private String form(Model model, RegionForm form, Long id) {
        model.addAttribute("regionForm", form);
        model.addAttribute("editing", id != null);
        model.addAttribute("regionId", id);
        return FORM_VIEW;
    }
}
 