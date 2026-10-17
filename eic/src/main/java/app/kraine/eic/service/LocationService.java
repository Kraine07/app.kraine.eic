package app.kraine.eic.service;

import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.exception.DuplicateLocationNameException;
import app.kraine.eic.exception.InvalidLocationReferenceException;
import app.kraine.eic.exception.LocationLinkedToEquipmentException;
import app.kraine.eic.exception.LocationNotFoundException;
import app.kraine.eic.form.LocationForm;
import app.kraine.eic.locationManagement.LocationEquipmentLinks;
import app.kraine.eic.locationManagement.LocationViews.LocationEditData;
import app.kraine.eic.locationManagement.LocationViews.LocationRow;
import app.kraine.eic.model.Location;
import app.kraine.eic.model.LocationId;
import app.kraine.eic.model.Property;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.propertyManagement.PropertyViews.Option;
import app.kraine.eic.repository.LocationRepoInterface;
import app.kraine.eic.repository.PropertyRepoInterface;
import app.kraine.eic.repository.UserRepoInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * LM-1 to LM-4 / UC-20 to UC-22. Administrators manage every location; an Editor manages the
 * locations of the properties assigned to them (LM-4). Every change is audited (UA-25).
 *
 * <p>A location is identified by its property and name (a composite key), which shapes two things:
 * <ul>
 *   <li>Renaming changes the primary key. It is done with one UPDATE statement, and it only works while
 *       no equipment points at the location, because equipment references that key.</li>
 *   <li>Deleting goes through the property: it owns its locations (orphanRemoval), so the location is
 *       removed from the property's list. Deleting the location object alone would be undone by the
 *       cascade from the property.</li>
 * </ul>
 *
 * <p>Needs these on your repositories:
 * <ul>
 *   <li>{@code LocationRepoInterface extends JpaRepository<Location, LocationId>}:
 *       {@code findByPropertyUserId(Long)}, {@code existsByPropertyIdAndNameIgnoreCase(Long, String)} and
 *       {@code rename(Long propertyId, String oldName, String newName)}</li>
 *   <li>{@code PropertyRepoInterface}: {@code findByUserId(Long)}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepoInterface locationRepository;
    private final PropertyRepoInterface propertyRepository;
    private final UserRepoInterface userRepository;
    private final AuditService auditService;
    private final ObjectProvider<LocationEquipmentLinks> equipmentLinks;

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<LocationRow> list(String actorEmail) {
        User actor = actorOf(actorEmail);
        List<Location> locations = isAdministrator(actor)
                ? locationRepository.findAll()
                : locationRepository.findByPropertyUserId(actor.getId());
        return locations.stream()
                .sorted(Comparator.comparing((Location l) -> l.getProperty().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Location::getName, String.CASE_INSENSITIVE_ORDER))
                .map(l -> new LocationRow(l.getProperty().getId(), l.getProperty().getName(), l.getName()))
                .toList();
    }

    /** The properties the user may add locations to: all of them for an Administrator, their own for an Editor. */
    @Transactional(readOnly = true)
    public List<Option> propertyOptions(String actorEmail) {
        User actor = actorOf(actorEmail);
        List<Property> properties = isAdministrator(actor)
                ? propertyRepository.findAll()
                : propertyRepository.findByUserId(actor.getId());
        return properties.stream()
                .sorted(Comparator.comparing(Property::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new Option(p.getId(), p.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LocationEditData findForEdit(Long propertyId, String name, String actorEmail) {
        User actor = actorOf(actorEmail);
        Location location = find(propertyId, name);
        assertCanManage(actor, location.getProperty());

        LocationForm form = new LocationForm();
        form.setPropertyId(propertyId);
        form.setName(location.getName());
        form.setOriginalName(location.getName());
        return new LocationEditData(propertyId, location.getProperty().getName(), form);
    }

    // ------------------------------------------------------------------ UC-20 create

    @Transactional
    public void createLocation(LocationForm form, String actorEmail) {
        User actor = actorOf(actorEmail);
        Property property = propertyRepository.findById(form.getPropertyId()).orElseThrow(() ->
                new InvalidLocationReferenceException("propertyId", "The selected property no longer exists."));
        assertCanManage(actor, property);

        String name = form.getName().trim();
        if (locationRepository.existsByPropertyIdAndNameIgnoreCase(property.getId(), name)) {   // step 6
            throw new DuplicateLocationNameException(name, property.getName());
        }
        try {
            locationRepository.saveAndFlush(new Location(property, name));                      // step 7
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateLocationNameException(name, property.getName());
        }
        auditService.record(AuditEventType.LOCATION_CREATED, actorEmail, label(property.getName(), name), null);
        log.info("Location {} created by {}", label(property.getName(), name), actorEmail);
    }

    // ------------------------------------------------------------------ UC-21 update (rename)

    @Transactional
    public void updateLocation(LocationForm form, String actorEmail) {
        User actor = actorOf(actorEmail);
        Location location = find(form.getPropertyId(), form.getOriginalName());
        Property property = location.getProperty();
        assertCanManage(actor, property);

        Long propertyId = property.getId();
        String propertyName = property.getName();
        String oldName = location.getName();
        String newName = form.getName().trim();
        if (oldName.equals(newName)) {
            return; // nothing to change
        }
        // A change of letter case only ("store" to "Store") is not a clash with itself.
        if (!oldName.equalsIgnoreCase(newName)
                && locationRepository.existsByPropertyIdAndNameIgnoreCase(propertyId, newName)) {
            throw new DuplicateLocationNameException(newName, propertyName);
        }
        LocationEquipmentLinks links = equipmentLinks.getIfAvailable();
        if (links != null && links.isLinkedToEquipment(propertyId, oldName)) {
            throw LocationLinkedToEquipmentException.forRename();
        }

        int updated;
        try {
            updated = locationRepository.rename(propertyId, oldName, newName);                  // step 7
        } catch (DataIntegrityViolationException ex) {
            // Without a LocationEquipmentLinks bean, the foreign key from equipment is what stops this.
            throw LocationLinkedToEquipmentException.forRename();
        }
        if (updated == 0) {
            throw new LocationNotFoundException(propertyId, oldName);
        }
        auditService.record(AuditEventType.LOCATION_UPDATED, actorEmail, label(propertyName, newName),
                "Renamed from " + oldName);
        log.info("Location {} renamed to {} by {}", label(propertyName, oldName), newName, actorEmail);
    }

    // ------------------------------------------------------------------ UC-22 delete

    @Transactional
    public void deleteLocation(Long propertyId, String name, String actorEmail) {
        User actor = actorOf(actorEmail);
        Location location = find(propertyId, name);
        Property property = location.getProperty();
        assertCanManage(actor, property);

        String locationName = location.getName();
        String propertyName = property.getName();
        LocationEquipmentLinks links = equipmentLinks.getIfAvailable();
        if (links != null && links.isLinkedToEquipment(propertyId, locationName)) {             // step 5
            throw LocationLinkedToEquipmentException.forDelete();
        }
        try {
            // The property owns its locations, so removing it from the list deletes it (orphanRemoval).
            boolean removed = property.getLocations().removeIf(l -> l.getName().equals(locationName));
            if (!removed) {
                locationRepository.delete(location);
            }
            locationRepository.flush();                                                         // step 6
        } catch (DataIntegrityViolationException ex) {
            throw LocationLinkedToEquipmentException.forDelete();
        }
        auditService.record(AuditEventType.LOCATION_DELETED, actorEmail, label(propertyName, locationName), null);
        log.info("Location {} deleted by {}", label(propertyName, locationName), actorEmail);
    }

    // ------------------------------------------------------------------ helpers

    private Location find(Long propertyId, String name) {
        return locationRepository.findById(new LocationId(propertyId, name))
                .orElseThrow(() -> new LocationNotFoundException(propertyId, name));
    }

    private User actorOf(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
    }

    private static boolean isAdministrator(User user) {
        return user.getRole().getRoleType() == RoleType.ADMINISTRATOR;
    }

    /** LM-4: an Administrator, or the editor the property is assigned to. */
    private static void assertCanManage(User actor, Property property) {
        if (isAdministrator(actor)) {
            return;
        }
        if (property.getUser() != null && Objects.equals(property.getUser().getId(), actor.getId())) {
            return;
        }
        throw new AccessDeniedException("You can only manage locations of the properties assigned to you.");
    }

    private static String label(String propertyName, String locationName) {
        return propertyName + " / " + locationName;
    }
}
