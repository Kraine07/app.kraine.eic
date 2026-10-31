package app.kraine.eic.property;

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

import app.kraine.eic.controller.PropertyController;
import app.kraine.eic.exception.DuplicatePropertyNameException;
import app.kraine.eic.exception.InvalidPropertyReferenceException;
import app.kraine.eic.exception.PropertyLinkedToEquipmentException;
import app.kraine.eic.form.PropertyForm;
import app.kraine.eic.propertyManagement.PropertyViews;
import app.kraine.eic.service.PropertyService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyControllerTest {

    private static final String ADMIN = "admin@example.com";

    @Mock private PropertyService propertyService;

    private final Model model = new ConcurrentModel();
    private final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
    private final Authentication admin = UsernamePasswordAuthenticationToken.authenticated(ADMIN, null, List.of());

    private PropertyController controller() {
        return new PropertyController(propertyService);
    }

    private static PropertyForm form() {
        PropertyForm form = new PropertyForm();
        form.setName("North Site");
        form.setRegionId(1L);
        return form;
    }

    private static BindingResult resultFor(PropertyForm form) {
        return new BeanPropertyBindingResult(form, "propertyForm");
    }

    @Test
    void listShowsTheProperties() {
        when(propertyService.list()).thenReturn(List.of());

        assertEquals("properties/list", controller().list(model));
        assertNotNull(model.getAttribute("properties"));
    }

    @Test
    void createRedirectsToTheListWithAConfirmation() {
        PropertyForm form = form();

        String view = controller().create(form, resultFor(form), model, redirect, admin);

        assertEquals("redirect:/properties", view);
        assertEquals("Property created.", redirect.getFlashAttributes().get("successMessage"));
        verify(propertyService).createProperty(form, ADMIN);
    }

    @Test
    void createShowsADuplicateNameUnderTheNameField() {
        PropertyForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new DuplicatePropertyNameException("North Site")).when(propertyService).createProperty(any(), any());

        String view = controller().create(form, result, model, redirect, admin);

        assertEquals("properties/form", view);
        assertTrue(result.hasFieldErrors("name"));
        assertEquals(Boolean.FALSE, model.getAttribute("editing"));
    }

    @Test
    void createShowsAnUnusableEditorUnderTheEditorField() {
        PropertyForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new InvalidPropertyReferenceException("editorId", "Only an Editor can be assigned to a property."))
                .when(propertyService).createProperty(any(), any());

        controller().create(form, result, model, redirect, admin);

        assertTrue(result.hasFieldErrors("editorId"));
    }

    @Test
    void updateShowsADuplicateNameAndKeepsTheLocationCount() {
        PropertyForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new DuplicatePropertyNameException("North Site")).when(propertyService).updateProperty(any(), any(), any());
        when(propertyService.findForEdit(5L)).thenReturn(new PropertyViews.PropertyEditData(5L, new PropertyForm(), 2));

        String view = controller().update(5L, form, result, model, redirect, admin);

        assertEquals("properties/form", view);
        assertTrue(result.hasFieldErrors("name"));
        assertEquals(Boolean.TRUE, model.getAttribute("editing"));
        assertEquals(2, model.getAttribute("locationCount"));
    }

    @Test
    void deleteExplainsWhyAPropertyWithEquipmentCannotBeDeleted() {
        doThrow(new PropertyLinkedToEquipmentException()).when(propertyService).deleteProperty(4L, ADMIN);

        String view = controller().delete(4L, redirect, admin);

        assertEquals("redirect:/properties", view);
        assertTrue(redirect.getFlashAttributes().get("errorMessage").toString().contains("has equipment"));
    }

    @Test
    void deleteConfirmsOnSuccess() {
        controller().delete(4L, redirect, admin);

        assertEquals("Property deleted.", redirect.getFlashAttributes().get("successMessage"));
    }
}
