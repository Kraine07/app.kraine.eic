package app.kraine.eic.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import app.kraine.eic.equipmentManagement.EquipmentRow;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentEditData;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentPage;
import app.kraine.eic.exception.DuplicateSerialNumberException;
import app.kraine.eic.exception.EquipmentInUseException;
import app.kraine.eic.exception.InvalidEquipmentReferenceException;
import app.kraine.eic.form.EquipmentForm;
import app.kraine.eic.service.EquipmentService;
 
/**
 * The 'Equipment management' screen (EQM-1 to EQM-4): list, details, create, update and delete.
 *
 * <p>Note: {@code Model} here is Spring's page model, not the equipment model entity. The details page at
 * /equipment/{id} is where the image upload (UC-29) starts and where it returns to.
 */
@Controller
@RequestMapping("/equipment")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'EDITOR')")
@RequiredArgsConstructor
public class EquipmentController {
 
    static final String LIST_VIEW = "equipment/list";
    static final String DETAIL_VIEW = "equipment/detail";
    static final String FORM_VIEW = "equipment/form";
    static final String LIST_REDIRECT = "redirect:/equipment";
 
    private final EquipmentService equipmentService;
 
    // ------------------------------------------------------------------ list and details
 
    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
                    @RequestParam(defaultValue = "0") int page,
                    Model model,
                    Authentication authentication) {
        Page<EquipmentRow> results = equipmentService.search(q, page, authentication.getName());
        model.addAttribute("results", new EquipmentPage(results.getContent(), results.getNumber(),
                results.getTotalPages(), results.getTotalElements()));
        model.addAttribute("q", q.trim());
        return LIST_VIEW;
    }
 
    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model, Authentication authentication) {
        model.addAttribute("equipment", equipmentService.findDetail(id, authentication.getName()));
        return DETAIL_VIEW;
    }
 
    // ------------------------------------------------------------------ create (UC-23)
 
    @GetMapping("/new")
    public String newForm(Model model, Authentication authentication) {
        return form(model, new EquipmentForm(), null, authentication.getName());
    }
 
    @PostMapping
    public String create(@Valid @ModelAttribute("equipmentForm") EquipmentForm form,
                    BindingResult result,
                    Model model,
                    RedirectAttributes redirect,
                    Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                equipmentService.createEquipment(form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Equipment created.");
                return LIST_REDIRECT;
            } catch (DuplicateSerialNumberException ex) {
                result.rejectValue("serialNumber", "duplicate", "This serial number is already in use.");
            } catch (InvalidEquipmentReferenceException ex) {
                result.rejectValue(ex.getField(), "invalid", ex.getMessage());
            }
        }
        return form(model, form, null, authentication.getName());
    }
 
    // ------------------------------------------------------------------ update (UC-24)
 
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, Authentication authentication) {
        EquipmentEditData data = equipmentService.findForEdit(id, authentication.getName());
        return form(model, data.form(), id, authentication.getName());
    }
 
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                    @Valid @ModelAttribute("equipmentForm") EquipmentForm form,
                    BindingResult result,
                    Model model,
                    RedirectAttributes redirect,
                    Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                equipmentService.updateEquipment(id, form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Equipment updated.");
                return LIST_REDIRECT;
            } catch (DuplicateSerialNumberException ex) {
                result.rejectValue("serialNumber", "duplicate", "This serial number is already in use.");
            } catch (InvalidEquipmentReferenceException ex) {
                result.rejectValue(ex.getField(), "invalid", ex.getMessage());
            }
        }
        return form(model, form, id, authentication.getName());
    }
 
    // ------------------------------------------------------------------ delete (UC-25)
 
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect, Authentication authentication) {
        try {
            equipmentService.deleteEquipment(id, authentication.getName());
            redirect.addFlashAttribute("successMessage", "Equipment deleted.");
        } catch (EquipmentInUseException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return LIST_REDIRECT;
    }
 
    // ------------------------------------------------------------------ view helper
 
    /** [id] is null when creating. */
    private String form(Model model, EquipmentForm form, Long id, String actorEmail) {
        model.addAttribute("equipmentForm", form);
        model.addAttribute("editing", id != null);
        model.addAttribute("equipmentId", id);
        model.addAttribute("models", equipmentService.modelOptions());
        model.addAttribute("locations", equipmentService.locationOptions(actorEmail));
        return FORM_VIEW;
    }
}