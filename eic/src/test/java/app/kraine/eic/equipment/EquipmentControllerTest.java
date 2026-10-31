package app.kraine.eic.equipment;

import app.kraine.eic.controller.EquipmentController;
import app.kraine.eic.equipmentManagement.CompositeKeys;
import app.kraine.eic.equipmentManagement.EquipmentRow;
import app.kraine.eic.equipmentManagement.EquipmentViews;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentDetail;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentEditData;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentPage;
import app.kraine.eic.exception.DuplicateSerialNumberException;
import app.kraine.eic.exception.EquipmentInUseException;
import app.kraine.eic.exception.InvalidEquipmentReferenceException;
import app.kraine.eic.form.EquipmentForm;
import app.kraine.eic.service.EquipmentService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentControllerTest {

    private static final String USER = "editor@example.com";

    @Mock private EquipmentService equipmentService;

    private final Model pageModel = new ConcurrentModel();
    private final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
    private final Authentication user = UsernamePasswordAuthenticationToken.authenticated(USER, null, List.of());

    private EquipmentController controller() {
        return new EquipmentController(equipmentService);
    }

    private static EquipmentForm form() {
        EquipmentForm form = new EquipmentForm();
        form.setSerialNumber("SN-1");
        form.setModelKey(CompositeKeys.encode(1L, "X100"));
        form.setLocationKey(CompositeKeys.encode(10L, "Store"));
        return form;
    }

    private static BindingResult resultFor(EquipmentForm form) {
        return new BeanPropertyBindingResult(form, "equipmentForm");
    }

    @Test
    void listShowsOnePageOfResultsAndKeepsTheSearchText() {
        EquipmentRow row = new EquipmentRow(5L, "SN-1", YearMonth.of(2020, 5), "Acme", "X100", "North Site", "Store");
        when(equipmentService.search("acme", 1, USER))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 25), 30));

        String view = controller().list(" acme ".trim(), 1, pageModel, user);

        assertEquals("equipment/list", view);
        EquipmentPage results = (EquipmentPage) pageModel.getAttribute("results");
        assertEquals(1, results.rows().size());
        assertEquals(1, results.page());
        assertEquals(2, results.totalPages());
        assertTrue(results.hasPrevious());
        assertFalse(results.hasNext());
        assertEquals("acme", pageModel.getAttribute("q"));
    }

    @Test
    void detailsShowsTheEquipment() {
        EquipmentDetail detail = new EquipmentDetail(5L, "SN-1", null, "Acme", "X100", "North Site", "Store",
                "Ana Silva", null);
        when(equipmentService.findDetail(5L, USER)).thenReturn(detail);

        assertEquals("equipment/detail", controller().details(5L, pageModel, user));
        assertSame(detail, pageModel.getAttribute("equipment"));
    }

    @Test
    void createRedirectsToTheListWithAConfirmation() {
        EquipmentForm form = form();

        String view = controller().create(form, resultFor(form), pageModel, redirect, user);

        assertEquals("redirect:/equipment", view);
        assertEquals("Equipment created.", redirect.getFlashAttributes().get("successMessage"));
        verify(equipmentService).createEquipment(form, USER);
    }

    @Test
    void createShowsADuplicateSerialNumberUnderThatField() {
        EquipmentForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new DuplicateSerialNumberException("SN-1")).when(equipmentService).createEquipment(any(), any());

        String view = controller().create(form, result, pageModel, redirect, user);

        assertEquals("equipment/form", view);
        assertTrue(result.hasFieldErrors("serialNumber"));
        assertEquals(Boolean.FALSE, pageModel.getAttribute("editing"));
    }

    @Test
    void createShowsAnUnusableLocationUnderThatField() {
        EquipmentForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new InvalidEquipmentReferenceException("locationKey", "The selected location no longer exists."))
                .when(equipmentService).createEquipment(any(), any());

        controller().create(form, result, pageModel, redirect, user);

        assertTrue(result.hasFieldErrors("locationKey"));
    }

    @Test
    void theEditFormShowsTheCurrentValues() {
        when(equipmentService.findForEdit(5L, USER)).thenReturn(new EquipmentEditData(5L, form()));

        String view = controller().editForm(5L, pageModel, user);

        assertEquals("equipment/form", view);
        assertEquals(Boolean.TRUE, pageModel.getAttribute("editing"));
        assertEquals(5L, pageModel.getAttribute("equipmentId"));
    }

    @Test
    void updateRedirectsToTheListWithAConfirmation() {
        EquipmentForm form = form();

        String view = controller().update(5L, form, resultFor(form), pageModel, redirect, user);

        assertEquals("redirect:/equipment", view);
        assertEquals("Equipment updated.", redirect.getFlashAttributes().get("successMessage"));
        verify(equipmentService).updateEquipment(5L, form, USER);
    }

    @Test
    void updateShowsADuplicateSerialNumberAndStaysOnTheEditForm() {
        EquipmentForm form = form();
        BindingResult result = resultFor(form);
        doThrow(new DuplicateSerialNumberException("SN-1")).when(equipmentService).updateEquipment(any(), any(), any());

        String view = controller().update(5L, form, result, pageModel, redirect, user);

        assertEquals("equipment/form", view);
        assertTrue(result.hasFieldErrors("serialNumber"));
        assertEquals(Boolean.TRUE, pageModel.getAttribute("editing"));
        assertEquals(5L, pageModel.getAttribute("equipmentId"));
    }

    @Test
    void deleteConfirmsOnSuccess() {
        controller().delete(5L, redirect, user);

        assertEquals("Equipment deleted.", redirect.getFlashAttributes().get("successMessage"));
        verify(equipmentService).deleteEquipment(5L, USER);
    }

    @Test
    void deleteExplainsWhyEquipmentThatOtherRecordsUseCannotBeDeleted() {
        doThrow(new EquipmentInUseException()).when(equipmentService).deleteEquipment(5L, USER);

        String view = controller().delete(5L, redirect, user);

        assertEquals("redirect:/equipment", view);
        assertTrue(redirect.getFlashAttributes().get("errorMessage").toString().contains("referred to"));
    }

    @Test
    void servesTheImageBytesWithTheirTypeAndNoSniffing() {
        byte[] png = {1, 2, 3};
        when(equipmentService.findImageContent(5L, USER))
                .thenReturn(Optional.of(new EquipmentViews.ImageContent("image/png", png)));

        ResponseEntity<byte[]> response = controller().imageContent(5L, user);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertTrue(response.getHeaders().getCacheControl().contains("private"));
        assertArrayEquals(png, response.getBody());
    }

    @Test
    void answers404WhenTheEquipmentHasNoImage() {
        when(equipmentService.findImageContent(5L, USER)).thenReturn(Optional.empty());

        assertEquals(404, controller().imageContent(5L, user).getStatusCode().value());
    }
}
