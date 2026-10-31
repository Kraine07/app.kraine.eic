package app.kraine.eic.manufacturer;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import app.kraine.eic.controller.ManufacturerController;
import app.kraine.eic.exception.DuplicateManufacturerNameException;
import app.kraine.eic.exception.ManufacturerInUseException;
import app.kraine.eic.form.ManufacturerForm;
import app.kraine.eic.manufacturerManagement.ManufacturerViews.ManufacturerEditData;
import app.kraine.eic.service.ManufacturerService;

import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManufacturerControllerTest {

    private static final String ADMIN = "admin@example.com";

    @Mock private ManufacturerService manufacturerService;

    private final Model model = new ConcurrentModel();
    private final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
    private final Authentication admin = UsernamePasswordAuthenticationToken.authenticated(ADMIN, null, List.of());

    private ManufacturerController controller() {
        return new ManufacturerController(manufacturerService);
    }

    private static ManufacturerForm form() {
        ManufacturerForm form = new ManufacturerForm();
        form.setName("Acme");
        return form;
    }

    private static BindingResult resultFor(ManufacturerForm form) {
        return new BeanPropertyBindingResult(form, "manufacturerForm");
    }

    @Test
    void listShowsTheManufacturers() {
        when(manufacturerService.list()).thenReturn(List.of());

        assertEquals("manufacturers/list", controller().list(model));
        assertNotNull(model.getAttribute("manufacturers"));
    }

    @Test
    void createRedirectsToTheListWithAConfirmation() {
        ManufacturerForm form = form();

        String view = controller().create(form, resultFor(form), model, redirect, admin);

        assertEquals("redirect:/manufacturers", view);
        assertEquals("Manufacturer created.", redirect.getFlashAttributes().get("successMessage"));
        verify(manufacturerService).createManufacturer(form, ADMIN);
    }

    @Test
    void createShowsADuplicateNameUnderTheNameField() {
        ManufacturerForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new DuplicateManufacturerNameException("Acme")).when(manufacturerService)
                .createManufacturer(any(), any());

        String view = controller().create(form, result, model, redirect, admin);

        assertEquals("manufacturers/form", view);
        assertTrue(result.hasFieldErrors("name"));
        assertEquals(Boolean.FALSE, model.getAttribute("editing"));
    }

    @Test
    void theEditFormShowsTheCurrentName() {
        when(manufacturerService.findForEdit(3L)).thenReturn(new ManufacturerEditData(3L, form()));

        String view = controller().editForm(3L, model);

        assertEquals("manufacturers/form", view);
        assertEquals(Boolean.TRUE, model.getAttribute("editing"));
        assertEquals(3L, model.getAttribute("manufacturerId"));
    }

    @Test
    void updateRedirectsToTheListWithAConfirmation() {
        ManufacturerForm form = form();

        String view = controller().update(3L, form, resultFor(form), model, redirect, admin);

        assertEquals("redirect:/manufacturers", view);
        assertEquals("Manufacturer updated.", redirect.getFlashAttributes().get("successMessage"));
        verify(manufacturerService).updateManufacturer(3L, form, ADMIN);
    }

    @Test
    void updateShowsADuplicateNameAndStaysOnTheEditForm() {
        ManufacturerForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new DuplicateManufacturerNameException("Acme")).when(manufacturerService)
                .updateManufacturer(any(), any(), any());

        String view = controller().update(3L, form, result, model, redirect, admin);

        assertEquals("manufacturers/form", view);
        assertTrue(result.hasFieldErrors("name"));
        assertEquals(Boolean.TRUE, model.getAttribute("editing"));
        assertEquals(3L, model.getAttribute("manufacturerId"));
    }

    @Test
    void deleteExplainsWhyAManufacturerInUseCannotBeDeleted() {
        doThrow(new ManufacturerInUseException()).when(manufacturerService).deleteManufacturer(3L, ADMIN);

        String view = controller().delete(3L, redirect, admin);

        assertEquals("redirect:/manufacturers", view);
        assertTrue(redirect.getFlashAttributes().get("errorMessage").toString().contains("linked"));
    }

    @Test
    void deleteConfirmsOnSuccess() {
        controller().delete(3L, redirect, admin);

        assertEquals("Manufacturer deleted.", redirect.getFlashAttributes().get("successMessage"));
    }
}
