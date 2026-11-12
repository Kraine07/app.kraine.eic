package app.kraine.eic.api;

import app.kraine.eic.exception.InvalidLocationReferenceException;
import app.kraine.eic.form.LocationForm;
import app.kraine.eic.locationManagement.LocationViews.LocationEditData;
import app.kraine.eic.locationManagement.LocationViews.LocationRow;
import app.kraine.eic.propertyManagement.PropertyViews.Option;
import app.kraine.eic.service.LocationService;
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
 * JSON version of the location management pages (LM-1 to LM-4, UC-20 to UC-22), for the Flutter app.
 * Administrators manage every location; an Editor only those of the properties assigned to them. Every call
 * goes through the same LocationService as the web pages, so the same checks apply: unique name within the
 * property, a location with equipment can be neither renamed nor deleted, and an Editor cannot reach other
 * people's properties (403).
 *
 * <p>A location is identified by its property and its name, and a name can contain any character, so a single
 * location is addressed with query parameters (propertyId and name) rather than path segments.
 */
@RestController
@RequestMapping("/api/locations")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'EDITOR')")
@RequiredArgsConstructor
public class LocationApiController {

    private final LocationService locationService;

    public record LocationsResponse(List<LocationRow> locations) {
    }

    /** The properties the user may add locations to. */
    public record FormOptionsResponse(List<Option> properties) {
    }

    public record LocationDetailResponse(Long propertyId, String propertyName, String name) {
    }

    @GetMapping
    public LocationsResponse list(Authentication authentication) {
        return new LocationsResponse(locationService.list(authentication.getName()));
    }

    @GetMapping("/form-options")
    public FormOptionsResponse formOptions(Authentication authentication) {
        return new FormOptionsResponse(locationService.propertyOptions(authentication.getName()));
    }

    @GetMapping("/detail")
    public LocationDetailResponse get(@RequestParam Long propertyId, @RequestParam String name,
                                      Authentication authentication) {
        LocationEditData data = locationService.findForEdit(propertyId, name, authentication.getName());
        return new LocationDetailResponse(data.propertyId(), data.propertyName(), data.form().getName());
    }

    /** UC-20: 201 when created. */
    @PostMapping
    public ResponseEntity<LocationDetailResponse> create(@Valid @RequestBody LocationForm form,
                                                         Authentication authentication) {
        locationService.createLocation(form, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LocationDetailResponse(form.getPropertyId(), null, form.getName().trim()));
    }

    /** UC-21: renames the location named originalName. The property cannot be changed. */
    @PutMapping
    public ResponseEntity<Void> update(@Valid @RequestBody LocationForm form, Authentication authentication) {
        if (form.getOriginalName() == null || form.getOriginalName().isBlank()) {
            throw new InvalidLocationReferenceException("originalName", "Say which location is being renamed.");
        }
        locationService.updateLocation(form, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** UC-22. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam Long propertyId, @RequestParam String name,
                                       Authentication authentication) {
        locationService.deleteLocation(propertyId, name, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
