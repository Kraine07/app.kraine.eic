package app.kraine.eic.modelmanagement;

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

import app.kraine.eic.controller.ModelController;
import app.kraine.eic.exception.DuplicateModelDescriptionException;
import app.kraine.eic.exception.InvalidModelReferenceException;
import app.kraine.eic.exception.ModelLinkedToEquipmentException;
import app.kraine.eic.form.ModelForm;
import app.kraine.eic.modelManagement.ModelViews.ModelEditData;
import app.kraine.eic.service.ModelService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelControllerTest {

    private static final String ADMIN = "admin@example.com";

    @Mock private ModelService modelService;

    private final Model pageModel = new ConcurrentModel();
    private final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
    private final Authentication admin = UsernamePasswordAuthenticationToken.authenticated(ADMIN, null, List.of());

    private ModelController controller() {
        return new ModelController(modelService);
    }

    private static ModelForm form() {
        ModelForm form = new ModelForm();
        form.setManufacturerId(1L);
        form.setDescription("X100");
        form.setOriginalManufacturerId(1L);
        form.setOriginalDescription("X100");
        return form;
    }

    private static BindingResult resultFor(ModelForm form) {
        return new BeanPropertyBindingResult(form, "modelForm");
    }

    @Test
    void listShowsTheModels() {
        when(modelService.list()).thenReturn(List.of());

        assertEquals("models/list", controller().list(pageModel));
        assertNotNull(pageModel.getAttribute("models"));
    }

    @Test
    void createRedirectsToTheListWithAConfirmation() {
        ModelForm form = form();

        String view = controller().create(form, resultFor(form), pageModel, redirect, admin);

        assertEquals("redirect:/models", view);
        assertEquals("Model created.", redirect.getFlashAttributes().get("successMessage"));
        verify(modelService).createModel(form, ADMIN);
    }

    @Test
    void createShowsADuplicateUnderTheDescriptionField() {
        ModelForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new DuplicateModelDescriptionException("X100", "Acme")).when(modelService).createModel(any(), any());

        String view = controller().create(form, result, pageModel, redirect, admin);

        assertEquals("models/form", view);
        assertTrue(result.hasFieldErrors("description"));
        assertEquals(Boolean.FALSE, pageModel.getAttribute("editing"));
    }

    @Test
    void createShowsAnUnusableManufacturerUnderThatField() {
        ModelForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new InvalidModelReferenceException("manufacturerId", "The selected manufacturer no longer exists."))
                .when(modelService).createModel(any(), any());

        controller().create(form, result, pageModel, redirect, admin);

        assertTrue(result.hasFieldErrors("manufacturerId"));
    }

    @Test
    void theEditFormShowsTheCurrentValues() {
        when(modelService.findForEdit(1L, "X100")).thenReturn(new ModelEditData(form()));

        String view = controller().editForm(1L, "X100", pageModel);

        assertEquals("models/form", view);
        assertEquals(Boolean.TRUE, pageModel.getAttribute("editing"));
    }

    @Test
    void updateRedirectsToTheListWithAConfirmation() {
        ModelForm form = form();

        String view = controller().update(form, resultFor(form), pageModel, redirect, admin);

        assertEquals("redirect:/models", view);
        assertEquals("Model updated.", redirect.getFlashAttributes().get("successMessage"));
        verify(modelService).updateModel(form, ADMIN);
    }

    @Test
    void updateShowsADuplicateAndStaysOnTheEditForm() {
        ModelForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new DuplicateModelDescriptionException("X100", "Acme")).when(modelService).updateModel(any(), any());

        String view = controller().update(form, result, pageModel, redirect, admin);

        assertEquals("models/form", view);
        assertTrue(result.hasFieldErrors("description"));
        assertEquals(Boolean.TRUE, pageModel.getAttribute("editing"));
    }

    @Test
    void updateExplainsWhyAModelThatEquipmentUsesCannotBeChanged() {
        ModelForm form = form();
        BindingResult result = resultFor(form);
        doThrow(ModelLinkedToEquipmentException.forChange()).when(modelService).updateModel(any(), any());

        controller().update(form, result, pageModel, redirect, admin);

        assertTrue(result.getFieldError("description").getDefaultMessage().contains("cannot be changed"));
    }

    @Test
    void deleteExplainsWhyAModelThatEquipmentUsesCannotBeDeleted() {
        doThrow(ModelLinkedToEquipmentException.forDelete()).when(modelService).deleteModel(1L, "X100", ADMIN);

        String view = controller().delete(1L, "X100", redirect, admin);

        assertEquals("redirect:/models", view);
        assertTrue(redirect.getFlashAttributes().get("errorMessage").toString().contains("linked to equipment"));
    }

    @Test
    void deleteConfirmsOnSuccess() {
        controller().delete(1L, "X100", redirect, admin);

        assertEquals("Model deleted.", redirect.getFlashAttributes().get("successMessage"));
    }
}
