package app.kraine.eic.api;

import app.kraine.eic.equipmentManagement.EquipmentRow;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentDetail;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentEditData;
import app.kraine.eic.equipmentManagement.EquipmentViews.Option;
import app.kraine.eic.form.EquipmentForm;
import app.kraine.eic.model.Equipment;
import app.kraine.eic.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * JSON version of the equipment pages (EQM-1 to EQM-4, UC-23 to UC-25 and the details page), for the Flutter
 * app. Administrators work with all equipment; an Editor only with the equipment at the properties assigned to
 * them. Every call goes through the same EquipmentService as the web pages, so the same checks apply (unique
 * serial number, a usable model and location, a manufactured month that is not in the future) and an Editor
 * reaching for someone else's equipment gets a 403 from the security layer.
 *
 * <p>Models and locations are chosen by an opaque [key] (see CompositeKeys): the app takes it from the choices
 * and sends it back, and never builds one itself.
 */
@RestController
@RequestMapping("/api/equipment")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'EDITOR')")
@RequiredArgsConstructor
public class EquipmentApiController {

    private final EquipmentService equipmentService;

    /** One line of the list. [manufactured] is a month such as 2026-10, or null. */
    public record EquipmentItem(Long id, String serialNumber, String manufactured, String manufacturerName,
                                String modelDescription, String propertyName, String locationName) {
    }

    public record EquipmentListResponse(List<EquipmentItem> items, int page, int totalPages, long totalElements) {
    }

    public record EquipmentDetailResponse(Long id, String serialNumber, String manufactured, String manufacturerName,
                                          String modelDescription, String propertyName, String locationName,
                                          String editorName, String imageName) {
    }

    /** The models and locations to choose from in the New Equipment form. */
    public record FormOptionsResponse(List<Option> models, List<Option> locations) {
    }

    /** One item for the Edit Equipment form, with the choices. */
    public record EquipmentEditResponse(Long id, String serialNumber, String manufactured, String modelKey,
                                        String locationKey, List<Option> models, List<Option> locations) {
    }

    public record CreatedEquipmentResponse(Long id, String serialNumber) {
    }

    @GetMapping
    public EquipmentListResponse list(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "0") int page,
                                      Authentication authentication) {
        Page<EquipmentRow> result = equipmentService.search(q, page, authentication.getName());
        List<EquipmentItem> items = result.getContent().stream()
                .map(row -> new EquipmentItem(row.id(), row.serialNumber(), month(row), row.manufacturerName(),
                        row.modelDescription(), row.propertyName(), row.locationName()))
                .toList();
        return new EquipmentListResponse(items, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    @GetMapping("/form-options")
    public FormOptionsResponse formOptions(Authentication authentication) {
        return new FormOptionsResponse(equipmentService.modelOptions(), equipmentService.locationOptions(authentication.getName()));
    }

    @GetMapping("/{id}")
    public EquipmentDetailResponse get(@PathVariable Long id, Authentication authentication) {
        EquipmentDetail detail = equipmentService.findDetail(id, authentication.getName());
        return new EquipmentDetailResponse(detail.id(), detail.serialNumber(),
                detail.manufacturedDate() == null ? null : detail.manufacturedDate().toString(),
                detail.manufacturerName(), detail.modelDescription(), detail.propertyName(), detail.locationName(),
                detail.editorName(), detail.imageName());
    }

    @GetMapping("/{id}/edit")
    public EquipmentEditResponse edit(@PathVariable Long id, Authentication authentication) {
        EquipmentEditData data = equipmentService.findForEdit(id, authentication.getName());
        EquipmentForm form = data.form();
        return new EquipmentEditResponse(data.id(), form.getSerialNumber(), form.getManufacturedDate(), form.getModelKey(),
                form.getLocationKey(), equipmentService.modelOptions(), equipmentService.locationOptions(authentication.getName()));
    }

    /** The equipment's picture, sent inline. 404 when it has none; 403 when the user may not see the equipment. */
    @GetMapping("/{id}/image/content")
    public ResponseEntity<byte[]> imageContent(@PathVariable Long id, Authentication authentication) {
        return equipmentService.findImageContent(id, authentication.getName())
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(image.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(image.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** UC-23: 201 when created. */
    @PostMapping
    public ResponseEntity<CreatedEquipmentResponse> create(@Valid @RequestBody EquipmentForm form, Authentication authentication) {
        Equipment created = equipmentService.createEquipment(form, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedEquipmentResponse(created.getId(), created.getSerialNumber()));
    }

    /** UC-24. */
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @Valid @RequestBody EquipmentForm form, Authentication authentication) {
        equipmentService.updateEquipment(id, form, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** UC-25: also deletes the equipment's image. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        equipmentService.deleteEquipment(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private static String month(EquipmentRow row) {
        return row.manufacturedDate() == null ? null : row.manufacturedDate().toString();
    }
}
