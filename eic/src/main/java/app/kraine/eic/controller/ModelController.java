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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import app.kraine.eic.exception.DuplicateModelDescriptionException;
import app.kraine.eic.exception.InvalidModelReferenceException;
import app.kraine.eic.exception.ModelLinkedToEquipmentException;
import app.kraine.eic.form.ModelForm;
import app.kraine.eic.service.ModelService;

/**
 * The 'Model management' screen and its forms (MOD-1 to MOD-4). Administrators only.
 *
 * <p>Note: {@code Model} here is Spring's page model, not the equipment model entity. A model is
 * identified by its manufacturer and description, and a description can contain characters that do not
 * belong in a URL path, so the edit and delete addresses carry them as query parameters.
 */
@Controller
@RequestMapping("/models")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class ModelController {

    static final String LIST_VIEW = "models/list";
    static final String FORM_VIEW = "models/form";
    static final String LIST_REDIRECT = "redirect:/models";

    private final ModelService modelService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("models", modelService.list());
        return LIST_VIEW;
    }

    // ------------------------------------------------------------------ create (UC-11)

    @GetMapping("/new")
    public String newForm(Model model) {
        return form(model, new ModelForm(), false);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("modelForm") ModelForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirect,
                         Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                modelService.createModel(form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Model created.");
                return LIST_REDIRECT;
            } catch (DuplicateModelDescriptionException ex) {
                result.rejectValue("description", "duplicate", ex.getMessage());
            } catch (InvalidModelReferenceException ex) {
                result.rejectValue(ex.getField(), "invalid", ex.getMessage());
            }
        }
        return form(model, form, false);
    }

    // ------------------------------------------------------------------ update (UC-12)

    @GetMapping("/edit")
    public String editForm(@RequestParam Long manufacturerId, @RequestParam String description, Model model) {
        return form(model, modelService.findForEdit(manufacturerId, description).form(), true);
    }

    @PostMapping("/update")
    public String update(@Valid @ModelAttribute("modelForm") ModelForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirect,
                         Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                modelService.updateModel(form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Model updated.");
                return LIST_REDIRECT;
            } catch (DuplicateModelDescriptionException ex) {
                result.rejectValue("description", "duplicate", ex.getMessage());
            } catch (ModelLinkedToEquipmentException ex) {
                result.rejectValue("description", "linked", ex.getMessage());
            } catch (InvalidModelReferenceException ex) {
                result.rejectValue(ex.getField(), "invalid", ex.getMessage());
            }
        }
        return form(model, form, true);
    }

    // ------------------------------------------------------------------ delete (UC-13)

    @PostMapping("/delete")
    public String delete(@RequestParam Long manufacturerId,
                    @RequestParam String description,
                    RedirectAttributes redirect,
                    Authentication authentication) {
        try {
            modelService.deleteModel(manufacturerId, description, authentication.getName());
            redirect.addFlashAttribute("successMessage", "Model deleted.");
        } catch (ModelLinkedToEquipmentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return LIST_REDIRECT;
    }

    // ------------------------------------------------------------------ view helper

    private String form(Model model, ModelForm form, boolean editing) {
        model.addAttribute("modelForm", form);
        model.addAttribute("editing", editing);
        model.addAttribute("manufacturers", modelService.manufacturerOptions());
        return FORM_VIEW;
    }
}
