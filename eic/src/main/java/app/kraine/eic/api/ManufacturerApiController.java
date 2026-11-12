package app.kraine.eic.api;

import app.kraine.eic.form.ManufacturerForm;
import app.kraine.eic.manufacturerManagement.ManufacturerViews.ManufacturerEditData;
import app.kraine.eic.manufacturerManagement.ManufacturerViews.ManufacturerRow;
import app.kraine.eic.model.Manufacturer;
import app.kraine.eic.service.ManufacturerService;
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
 * JSON version of the manufacturer management pages (MAN-1 to MAN-4, UC-08 to UC-10), for the Flutter app.
 * Administrators only. Every call goes through the same ManufacturerService as the web pages: the same checks
 * (unique name, no deleting a manufacturer that models or equipment still use) and the same audit entries.
 */
@RestController
@RequestMapping("/api/manufacturers")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class ManufacturerApiController {

    private final ManufacturerService manufacturerService;

    public record ManufacturersResponse(List<ManufacturerRow> manufacturers) {
    }

    public record ManufacturerResponse(Long id, String name) {
    }

    @GetMapping
    public ManufacturersResponse list() {
        return new ManufacturersResponse(manufacturerService.list());
    }

    @GetMapping("/{id}")
    public ManufacturerResponse get(@PathVariable Long id) {
        ManufacturerEditData data = manufacturerService.findForEdit(id);
        return new ManufacturerResponse(data.id(), data.form().getName());
    }

    /** UC-08: 201 when created. */
    @PostMapping
    public ResponseEntity<ManufacturerResponse> create(@Valid @RequestBody ManufacturerForm form,
                                                       Authentication authentication) {
        Manufacturer created = manufacturerService.createManufacturer(form, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ManufacturerResponse(created.getId(), created.getName()));
    }

    /** UC-09. */
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @Valid @RequestBody ManufacturerForm form,
                                       Authentication authentication) {
        manufacturerService.updateManufacturer(id, form, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** UC-10: refused with 409 while models or equipment still use the manufacturer. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        manufacturerService.deleteManufacturer(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
