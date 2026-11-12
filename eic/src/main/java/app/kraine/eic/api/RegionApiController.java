package app.kraine.eic.api;

import app.kraine.eic.form.RegionForm;
import app.kraine.eic.model.Region;
import app.kraine.eic.regionManagement.RegionViews.RegionEditData;
import app.kraine.eic.regionManagement.RegionViews.RegionRow;
import app.kraine.eic.service.RegionService;
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
 * JSON version of the region management pages (RM-1 to RM-4, UC-14 to UC-16), for the Flutter app.
 * Administrators only. Every call goes through the same RegionService as the web pages: the same checks
 * (unique name, no deleting a region a property belongs to) and the same audit entries.
 */
@RestController
@RequestMapping("/api/regions")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class RegionApiController {

    private final RegionService regionService;

    public record RegionsResponse(List<RegionRow> regions) {
    }

    public record RegionResponse(Long id, String name) {
    }

    @GetMapping
    public RegionsResponse list() {
        return new RegionsResponse(regionService.list());
    }

    @GetMapping("/{id}")
    public RegionResponse get(@PathVariable Long id) {
        RegionEditData data = regionService.findForEdit(id);
        return new RegionResponse(data.id(), data.form().getName());
    }

    /** UC-14: 201 when created. */
    @PostMapping
    public ResponseEntity<RegionResponse> create(@Valid @RequestBody RegionForm form, Authentication authentication) {
        Region created = regionService.createRegion(form, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegionResponse(created.getId(), created.getName()));
    }

    /** UC-15. */
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @Valid @RequestBody RegionForm form,
                                       Authentication authentication) {
        regionService.updateRegion(id, form, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** UC-16: refused with 409 while a property belongs to the region. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        regionService.deleteRegion(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
