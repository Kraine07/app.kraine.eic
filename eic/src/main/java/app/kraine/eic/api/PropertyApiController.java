package app.kraine.eic.api;

import app.kraine.eic.form.PropertyForm;
import app.kraine.eic.propertyManagement.PropertyViews.Option;
import app.kraine.eic.propertyManagement.PropertyViews.PropertyEditData;
import app.kraine.eic.propertyManagement.PropertyViews.PropertyRow;
import app.kraine.eic.service.PropertyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * JSON version of the property management pages (PM-1 to PM-4, UC-17 to UC-19), for the Flutter app.
 * Administrators only. Every call goes through the same PropertyService as the web pages: the same checks
 * (unique name, usable region and editor, equipment still placed there), the same audit entries.
 */
@RestController
@RequestMapping("/api/properties")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class PropertyApiController {

    private final PropertyService propertyService;

    public record PropertiesResponse(List<PropertyRow> properties) {
    }

    /** The choices for the New Property form. */
    public record FormOptionsResponse(List<Option> regions, List<Option> editors) {
    }

    /**
     * One property for the Edit Property form, with the choices. [editors] always includes the property's
     * current editor, even if that account has been suspended since.
     */
    public record PropertyDetailResponse(Long id, String name, Long regionId, Long editorId, int locationCount,
                                        List<Option> regions, List<Option> editors) {
    }

    public record CreatedPropertyResponse(Long id, String name) {
    }

    @GetMapping
    public PropertiesResponse list() {
        return new PropertiesResponse(propertyService.list());
    }

    @GetMapping("/form-options")
    public FormOptionsResponse formOptions() {
        return new FormOptionsResponse(propertyService.regionOptions(), propertyService.editorOptions(null));
    }

    @GetMapping("/{id}")
    public PropertyDetailResponse get(@PathVariable Long id) {
        PropertyEditData data = propertyService.findForEdit(id);
        PropertyForm form = data.form();
        return new PropertyDetailResponse(data.id(), form.getName(), form.getRegionId(), form.getEditorId(),
                data.locationCount(), propertyService.regionOptions(), propertyService.editorOptions(form.getEditorId()));
    }

    /** UC-17: 201 when created. */
    @PostMapping
    public ResponseEntity<CreatedPropertyResponse> create(@Valid @RequestBody PropertyForm form, Authentication authentication) {
        var created = propertyService.createProperty(form, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedPropertyResponse(created.getId(), created.getName()));
    }

    /** UC-18. */
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @Valid @RequestBody PropertyForm form, Authentication authentication) {
        propertyService.updateProperty(id, form, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** UC-19: also deletes the property's locations. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        propertyService.deleteProperty(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
