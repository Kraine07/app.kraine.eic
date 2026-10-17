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

import app.kraine.eic.exception.DuplicatePropertyNameException;
import app.kraine.eic.exception.InvalidPropertyReferenceException;
import app.kraine.eic.exception.PropertyLinkedToEquipmentException;
import app.kraine.eic.form.PropertyForm;
import app.kraine.eic.propertyManagement.PropertyViews;
import app.kraine.eic.service.PropertyService;

/** The 'Property Management' screen and its forms (PM-1 to PM-4). Administrators only. */
@Controller
@RequestMapping("/properties")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class PropertyController {

    static final String LIST_VIEW = "properties/list";
    static final String FORM_VIEW = "properties/form";
    static final String LIST_REDIRECT = "redirect:/properties";

    private final PropertyService propertyService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("properties", propertyService.list());
        return LIST_VIEW;
    }

    // ------------------------------------------------------------------ create (UC-17)

    @GetMapping("/new")
    public String newForm(Model model) {
        return createForm(model, new PropertyForm());
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("propertyForm") PropertyForm form,
                    BindingResult result,
                    Model model,
                    RedirectAttributes redirect,
                    Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                propertyService.createProperty(form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Property created.");
                return LIST_REDIRECT;
            } catch (DuplicatePropertyNameException ex) {
                result.rejectValue("name", "duplicate", "This property name is already in use.");
            } catch (InvalidPropertyReferenceException ex) {
                result.rejectValue(ex.getField(), "invalid", ex.getMessage());
            }
        }
        return createForm(model, form);
    }

    // ------------------------------------------------------------------ update (UC-18)

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        PropertyViews.PropertyEditData data = propertyService.findForEdit(id);
        return editForm(model, id, data.form(), data.locationCount());
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                    @Valid @ModelAttribute("propertyForm") PropertyForm form,
                    BindingResult result,
                    Model model,
                    RedirectAttributes redirect,
                    Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                propertyService.updateProperty(id, form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Property updated.");
                return LIST_REDIRECT;
            } catch (DuplicatePropertyNameException ex) {
                result.rejectValue("name", "duplicate", "This property name is already in use.");
            } catch (InvalidPropertyReferenceException ex) {
                result.rejectValue(ex.getField(), "invalid", ex.getMessage());
            }
        }
        return editForm(model, id, form, propertyService.findForEdit(id).locationCount());
    }

    // ------------------------------------------------------------------ delete (UC-19)

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect, Authentication authentication) {
        try {
            propertyService.deleteProperty(id, authentication.getName());
            redirect.addFlashAttribute("successMessage", "Property deleted.");
        } catch (PropertyLinkedToEquipmentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return LIST_REDIRECT;
    }

    // ------------------------------------------------------------------ view helpers

    private String createForm(Model model, PropertyForm form) {
        model.addAttribute("propertyForm", form);
        model.addAttribute("editing", false);
        model.addAttribute("regions", propertyService.regionOptions());
        model.addAttribute("editors", propertyService.editorOptions(form.getEditorId()));
        return FORM_VIEW;
    }

    private String editForm(Model model, Long id, PropertyForm form, int locationCount) {
        model.addAttribute("propertyForm", form);
        model.addAttribute("editing", true);
        model.addAttribute("propertyId", id);
        model.addAttribute("locationCount", locationCount);
        model.addAttribute("regions", propertyService.regionOptions());
        model.addAttribute("editors", propertyService.editorOptions(form.getEditorId()));
        return FORM_VIEW;
    }
}