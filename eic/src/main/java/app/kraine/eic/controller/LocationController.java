package app.kraine.eic.controller;

import app.kraine.eic.exception.DuplicateLocationNameException;
import app.kraine.eic.exception.InvalidLocationReferenceException;
import app.kraine.eic.exception.LocationLinkedToEquipmentException;
import app.kraine.eic.form.LocationForm;
import app.kraine.eic.locationManagement.LocationViews.LocationEditData;
import app.kraine.eic.service.LocationService;
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

/**
 * The 'Location Management' screen and its forms (LM-1 to LM-4). A location is identified by its
 * property and name, and a name can contain characters that do not belong in a URL path, so the
 * edit and delete addresses carry them as query parameters instead.
 */
@Controller
@RequestMapping("/locations")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'EDITOR')")
@RequiredArgsConstructor
public class LocationController {

    static final String LIST_VIEW = "locations/list";
    static final String FORM_VIEW = "locations/form";
    static final String LIST_REDIRECT = "redirect:/locations";

    private final LocationService locationService;

    @GetMapping
    public String list(Model model, Authentication authentication) {
        model.addAttribute("locations", locationService.list(authentication.getName()));
        return LIST_VIEW;
    }

    // ------------------------------------------------------------------ create (UC-20)

    @GetMapping("/new")
    public String newForm(Model model, Authentication authentication) {
        return createForm(model, new LocationForm(), authentication.getName());
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("locationForm") LocationForm form,
                    BindingResult result,
                    Model model,
                    RedirectAttributes redirect,
                    Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                locationService.createLocation(form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Location created.");
                return LIST_REDIRECT;
            } catch (DuplicateLocationNameException ex) {
                result.rejectValue("name", "duplicate", ex.getMessage());
            } catch (InvalidLocationReferenceException ex) {
                result.rejectValue(ex.getField(), "invalid", ex.getMessage());
            }
        }
        return createForm(model, form, authentication.getName());
    }

    // ------------------------------------------------------------------ update (UC-21)

    @GetMapping("/edit")
    public String editForm(@RequestParam Long propertyId,
                        @RequestParam String name,
                        Model model,
                        Authentication authentication) {
        return editForm(model, locationService.findForEdit(propertyId, name, authentication.getName()));
    }

    @PostMapping("/update")
    public String update(@Valid @ModelAttribute("locationForm") LocationForm form,
                        BindingResult result,
                        Model model,
                        RedirectAttributes redirect,
                        Authentication authentication) {
        if (!result.hasErrors()) {
            try {
                locationService.updateLocation(form, authentication.getName());
                redirect.addFlashAttribute("successMessage", "Location updated.");
                return LIST_REDIRECT;
            } catch (DuplicateLocationNameException ex) {
                result.rejectValue("name", "duplicate", ex.getMessage());
            } catch (LocationLinkedToEquipmentException ex) {
                result.rejectValue("name", "linked", ex.getMessage());
            }
        }
        LocationEditData data = locationService.findForEdit(form.getPropertyId(), form.getOriginalName(),
                authentication.getName());
        return editForm(model, new LocationEditData(data.propertyId(), data.propertyName(), form));
    }

    // ------------------------------------------------------------------ delete (UC-22)

    @PostMapping("/delete")
    public String delete(@RequestParam Long propertyId,
                        @RequestParam String name,
                        RedirectAttributes redirect,
                        Authentication authentication) {
        try {
            locationService.deleteLocation(propertyId, name, authentication.getName());
            redirect.addFlashAttribute("successMessage", "Location deleted.");
        } catch (LocationLinkedToEquipmentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return LIST_REDIRECT;
    }

    // ------------------------------------------------------------------ view helpers

    private String createForm(Model model, LocationForm form, String actorEmail) {
        model.addAttribute("locationForm", form);
        model.addAttribute("editing", false);
        model.addAttribute("properties", locationService.propertyOptions(actorEmail));
        return FORM_VIEW;
    }

    private String editForm(Model model, LocationEditData data) {
        model.addAttribute("locationForm", data.form());
        model.addAttribute("editing", true);
        model.addAttribute("propertyName", data.propertyName());
        return FORM_VIEW;
    }
}
