package app.kraine.eic.api;

import app.kraine.eic.exception.InvalidModelReferenceException;
import app.kraine.eic.form.ModelForm;
import app.kraine.eic.modelManagement.ModelViews.ModelEditData;
import app.kraine.eic.modelManagement.ModelViews.ModelRow;
import app.kraine.eic.modelManagement.ModelViews.Option;
import app.kraine.eic.service.ModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * JSON version of the model management pages (MOD-1 to MOD-4, UC-11 to UC-13), for the Flutter app.
 * Administrators only. Every call goes through the same ModelService as the web pages: the same checks (the
 * manufacturer must exist, a description is unique within its manufacturer, a model that equipment uses can
 * be neither changed nor deleted) and the same audit entries.
 *
 * <p>A model is identified by its manufacturer and its description, and a description can contain any
 * character, so a single model is addressed with query parameters rather than path segments.
 */
@RestController
@RequestMapping("/api/models")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class ModelApiController {

    private final ModelService modelService;

    public record ModelsResponse(List<ModelRow> models) {
    }

    /** The manufacturers to choose from in the New Model form. */
    public record FormOptionsResponse(List<Option> manufacturers) {
    }

    /** One model for the Edit Model form, with the manufacturers to choose from. */
    public record ModelDetailResponse(Long manufacturerId, String description, List<Option> manufacturers) {
    }

    public record CreatedModelResponse(Long manufacturerId, String description) {
    }

    @GetMapping
    public ModelsResponse list() {
        return new ModelsResponse(modelService.list());
    }

    @GetMapping("/form-options")
    public FormOptionsResponse formOptions() {
        return new FormOptionsResponse(modelService.manufacturerOptions());
    }

    @GetMapping("/detail")
    public ModelDetailResponse get(@RequestParam Long manufacturerId, @RequestParam String description) {
        ModelEditData data = modelService.findForEdit(manufacturerId, description);
        return new ModelDetailResponse(data.form().getManufacturerId(), data.form().getDescription(),
                modelService.manufacturerOptions());
    }

    /** UC-11: 201 when created. */
    @PostMapping
    public ResponseEntity<CreatedModelResponse> create(@Valid @RequestBody ModelForm form, Authentication authentication) {
        modelService.createModel(form, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedModelResponse(form.getManufacturerId(), form.getDescription().trim()));
    }

    /** UC-12: the original manufacturer and description say which model is being changed. */
    @PutMapping
    public ResponseEntity<Void> update(@Valid @RequestBody ModelForm form, Authentication authentication) {
        if (form.getOriginalManufacturerId() == null) {
            throw new InvalidModelReferenceException("originalManufacturerId", "Say which model is being changed.");
        }
        if (form.getOriginalDescription() == null || form.getOriginalDescription().isBlank()) {
            throw new InvalidModelReferenceException("originalDescription", "Say which model is being changed.");
        }
        modelService.updateModel(form, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** UC-13: refused with 409 while equipment uses the model. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam Long manufacturerId, @RequestParam String description,
                                       Authentication authentication) {
        modelService.deleteModel(manufacturerId, description, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
