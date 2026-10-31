package app.kraine.eic.location;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import app.kraine.eic.controller.LocationController;
import app.kraine.eic.exception.DuplicateLocationNameException;
import app.kraine.eic.exception.InvalidLocationReferenceException;
import app.kraine.eic.exception.LocationLinkedToEquipmentException;
import app.kraine.eic.form.LocationForm;
import app.kraine.eic.locationManagement.LocationViews.LocationEditData;
import app.kraine.eic.service.LocationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

    private static final String USER = "editor@example.com";

    @Mock private LocationService locationService;

    private final Model model = new ConcurrentModel();
    private final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
    private final Authentication user = UsernamePasswordAuthenticationToken.authenticated(USER, null, List.of());

    private LocationController controller() {
        return new LocationController(locationService);
    }

    private static LocationForm form() {
        LocationForm form = new LocationForm();
        form.setPropertyId(10L);
        form.setName("Store Room");
        form.setOriginalName("Store");
        return form;
    }

    private static BindingResult resultFor(LocationForm form) {
        return new BeanPropertyBindingResult(form, "locationForm");
    }

    @Test
    void listShowsTheLocationsOfTheSignedInUser() {
        when(locationService.list(USER)).thenReturn(List.of());

        assertEquals("locations/list", controller().list(model, user));
        assertNotNull(model.getAttribute("locations"));
    }

    @Test
    void createRedirectsToTheListWithAConfirmation() {
        LocationForm form = form();

        String view = controller().create(form, resultFor(form), model, redirect, user);

        assertEquals("redirect:/locations", view);
        assertEquals("Location created.", redirect.getFlashAttributes().get("successMessage"));
        verify(locationService).createLocation(form, USER);
    }

    @Test
    void createShowsADuplicateNameUnderTheNameField() {
        LocationForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new DuplicateLocationNameException("Store Room", "North Site"))
                .when(locationService).createLocation(any(), any());

        String view = controller().create(form, result, model, redirect, user);

        assertEquals("locations/form", view);
        assertTrue(result.hasFieldErrors("name"));
        assertEquals(Boolean.FALSE, model.getAttribute("editing"));
    }

    @Test
    void createShowsAnUnusablePropertyUnderThePropertyField() {
        LocationForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new InvalidLocationReferenceException("propertyId", "The selected property no longer exists."))
                .when(locationService).createLocation(any(), any());

        controller().create(form, result, model, redirect, user);

        assertTrue(result.hasFieldErrors("propertyId"));
    }

    @Test
    void theEditFormShowsThePropertyAndTheCurrentName() {
        when(locationService.findForEdit(10L, "Store", USER))
                .thenReturn(new LocationEditData(10L, "North Site", form()));

        String view = controller().editForm(10L, "Store", model, user);

        assertEquals("locations/form", view);
        assertEquals(Boolean.TRUE, model.getAttribute("editing"));
        assertEquals("North Site", model.getAttribute("propertyName"));
    }

    @Test
    void updateRedirectsToTheListWithAConfirmation() {
        LocationForm form = form();

        String view = controller().update(form, resultFor(form), model, redirect, user);

        assertEquals("redirect:/locations", view);
        assertEquals("Location updated.", redirect.getFlashAttributes().get("successMessage"));
        verify(locationService).updateLocation(form, USER);
    }

    @Test
    void updateShowsADuplicateNameAndKeepsTheEditForm() {
        LocationForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new DuplicateLocationNameException("Store Room", "North Site"))
                .when(locationService).updateLocation(any(), any());
        when(locationService.findForEdit(eq(10L), eq("Store"), eq(USER)))
                .thenReturn(new LocationEditData(10L, "North Site", new LocationForm()));

        String view = controller().update(form, result, model, redirect, user);

        assertEquals("locations/form", view);
        assertTrue(result.hasFieldErrors("name"));
        assertEquals(Boolean.TRUE, model.getAttribute("editing"));
        assertEquals("North Site", model.getAttribute("propertyName"));
    }

    @Test
    void updateExplainsWhyALocationWithEquipmentCannotBeRenamed() {
        LocationForm form = form();
        BindingResult result = resultFor(form);
        doThrow(LocationLinkedToEquipmentException.forRename()).when(locationService).updateLocation(any(), any());
        when(locationService.findForEdit(eq(10L), eq("Store"), eq(USER)))
                .thenReturn(new LocationEditData(10L, "North Site", new LocationForm()));

        controller().update(form, result, model, redirect, user);

        assertTrue(result.getFieldError("name").getDefaultMessage().contains("cannot be renamed"));
    }

    @Test
    void deleteExplainsWhyALocationWithEquipmentCannotBeDeleted() {
        doThrow(LocationLinkedToEquipmentException.forDelete()).when(locationService)
                .deleteLocation(10L, "Store", USER);

        String view = controller().delete(10L, "Store", redirect, user);

        assertEquals("redirect:/locations", view);
        assertTrue(redirect.getFlashAttributes().get("errorMessage").toString().contains("has equipment"));
    }

    @Test
    void deleteConfirmsOnSuccess() {
        controller().delete(10L, "Store", redirect, user);

        assertEquals("Location deleted.", redirect.getFlashAttributes().get("successMessage"));
    }
}
