package app.kraine.eic.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.exception.DuplicatePropertyNameException;
import app.kraine.eic.exception.InvalidPropertyReferenceException;
import app.kraine.eic.exception.PropertyLinkedToEquipmentException;
import app.kraine.eic.exception.PropertyNotFoundException;
import app.kraine.eic.form.PropertyForm;
import app.kraine.eic.model.Property;
import app.kraine.eic.model.Region;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.propertyManagement.PropertyEquipmentLinks;
import app.kraine.eic.propertyManagement.PropertyViews.Option;
import app.kraine.eic.propertyManagement.PropertyViews.PropertyEditData;
import app.kraine.eic.propertyManagement.PropertyViews.PropertyRow;
import app.kraine.eic.repository.PropertyRepoInterface;
import app.kraine.eic.repository.RegionRepoInterface;
import app.kraine.eic.repository.UserRepoInterface;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/**
 * PM-1 to PM-4 / UC-17 to UC-19: creating, updating and deleting properties. Every change is audited
 * with the acting Administrator (UA-25).
 *
 * <p>Needs these on your repositories:
 * <ul>
 *   <li>{@code PropertyRepoInterface}: {@code existsByNameIgnoreCase(String)},
 *       {@code existsByNameIgnoreCaseAndIdNot(String, Long)}</li>
 *   <li>{@code UserRepoInterface}: {@code findActiveByRoleType(RoleType)}</li>
 *   <li>{@code RegionRepoInterface extends JpaRepository<Region, Long>}, and {@code Region} with
 *       {@code getId()} and {@code getName()}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepoInterface propertyRepository;
    private final RegionRepoInterface regionRepository;
    private final UserRepoInterface userRepository;
    private final AuditService auditService;
    private final ObjectProvider<PropertyEquipmentLinks> equipmentLinks;

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<PropertyRow> list() {
        return propertyRepository.findAll().stream()
                .sorted(Comparator.comparing(Property::getName, String.CASE_INSENSITIVE_ORDER))
                .map(PropertyService::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public PropertyEditData findForEdit(Long id) {
        Property property = find(id);
        PropertyForm form = new PropertyForm();
        form.setName(property.getName());
        form.setRegionId(property.getRegion() == null ? null : property.getRegion().getId());
        form.setEditorId(property.getUser() == null ? null : property.getUser().getId());
        return new PropertyEditData(property.getId(), form, property.getLocations().size());
    }

    @Transactional(readOnly = true)
    public List<Option> regionOptions() {
        return regionRepository.findAll().stream()
                .sorted(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER))
                .map(region -> new Option(region.getId(), region.getName()))
                .toList();
    }

    /**
     * Active Editors, plus the property's current editor even if that account has since been
     * suspended, so the form can still show who is assigned.
     */
    @Transactional(readOnly = true)
    public List<Option> editorOptions(Long currentEditorId) {
        List<User> editors = new ArrayList<>(userRepository.findActiveByRoleRoleType(RoleType.EDITOR));
        if (currentEditorId != null && editors.stream().noneMatch(u -> currentEditorId.equals(u.getId()))) {
            userRepository.findById(currentEditorId).ifPresent(editors::add);
        }
        return editors.stream()
                .map(user -> new Option(user.getId(), user.getFirstName() + " " + user.getLastName() + " ("
                        + user.getEmail() + ")" + (Boolean.TRUE.equals(user.getIsSuspended()) ? " - suspended" : "")))
                .toList();
    }

    // ------------------------------------------------------------------ UC-17 create

    @Transactional
    public Property createProperty(PropertyForm form, String actor) {
        String name = form.getName().trim();
        if (propertyRepository.existsByNameIgnoreCase(name)) {                    // step 7
            throw new DuplicatePropertyNameException(name);
        }
        Region region = findRegion(form.getRegionId());
        User editor = findEditor(form.getEditorId(), null);

        Property property = new Property();
        property.setName(name);
        property.setRegion(region);
        property.setUser(editor);
        Property saved;
        try {
            saved = propertyRepository.saveAndFlush(property);                    // step 8
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatePropertyNameException(name); // another request used the name a moment ago
        }
        auditService.record(AuditEventType.PROPERTY_CREATED, actor, saved.getName(), describe(region, editor));
        log.info("Property {} created by {}", saved.getName(), actor);
        return saved;
    }

    // ------------------------------------------------------------------ UC-18 update

    @Transactional
    public void updateProperty(Long id, PropertyForm form, String actor) {
        Property property = find(id);
        String name = form.getName().trim();
        if (propertyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {        // step 7
            throw new DuplicatePropertyNameException(name);
        }
        Region region = findRegion(form.getRegionId());
        User editor = findEditor(form.getEditorId(), property.getUser());

        property.setName(name);
        property.setRegion(region);
        property.setUser(editor);
        try {
            propertyRepository.saveAndFlush(property);                            // step 8
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatePropertyNameException(name);
        }
        auditService.record(AuditEventType.PROPERTY_UPDATED, actor, name, describe(region, editor));
        log.info("Property {} updated by {}", name, actor);
    }

    // ------------------------------------------------------------------ UC-19 delete

    /**
     * Deleting a property also removes its locations (the entity cascades), so the check is whether
     * equipment is placed at any of them.
     */
    @Transactional
    public void deleteProperty(Long id, String actor) {
        Property property = find(id);
        PropertyEquipmentLinks links = equipmentLinks.getIfAvailable();
        if (links != null && links.isLinkedToEquipment(id)) {                     // step 5
            throw new PropertyLinkedToEquipmentException();
        }
        String name = property.getName();
        int locations = property.getLocations().size();
        try {
            propertyRepository.delete(property);                                  // step 6
            propertyRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // Without a PropertyEquipmentLinks bean, the foreign key from equipment is what stops this.
            throw new PropertyLinkedToEquipmentException();
        }
        auditService.record(AuditEventType.PROPERTY_DELETED, actor, name,
                locations == 0 ? null : locations + (locations == 1 ? " location removed" : " locations removed"));
        log.info("Property {} deleted by {}", name, actor);
    }

    // ------------------------------------------------------------------ helpers

    private Property find(Long id) {
        return propertyRepository.findById(id).orElseThrow(() -> new PropertyNotFoundException(id));
    }

    private Region findRegion(Long regionId) {
        if (regionId == null) {
            throw new InvalidPropertyReferenceException("regionId", "Select a region.");
        }
        return regionRepository.findById(regionId).orElseThrow(() ->
                new InvalidPropertyReferenceException("regionId", "The selected region no longer exists."));
    }

    /**
     * PM-2: only one editor per property. [current] is the editor already assigned (null when
     * creating); keeping a current editor who has since been suspended is allowed, picking a suspended
     * one is not.
     */
    private User findEditor(Long editorId, User current) {
        if (editorId == null) {
            return null;
        }
        User editor = userRepository.findById(editorId).orElseThrow(() ->
                new InvalidPropertyReferenceException("editorId", "The selected editor no longer exists."));
        if (editor.getRole().getRoleType() != RoleType.EDITOR) {
            throw new InvalidPropertyReferenceException("editorId", "Only an Editor can be assigned to a property.");
        }
        boolean unchanged = current != null && editorId.equals(current.getId());
        if (Boolean.TRUE.equals(editor.getIsSuspended()) && !unchanged) {
            throw new InvalidPropertyReferenceException("editorId", "The selected editor is suspended.");
        }
        return editor;
    }

    private static String describe(Region region, User editor) {
        return "Region " + region.getName() + (editor == null ? "" : ", editor " + editor.getEmail());
    }

    private static PropertyRow toRow(Property property) {
        String region = property.getRegion() == null ? "" : property.getRegion().getName();
        String editor = property.getUser() == null
                ? "Not assigned"
                : property.getUser().getFirstName() + " " + property.getUser().getLastName();
        return new PropertyRow(property.getId(), property.getName(), region, editor, property.getLocations().size());
    }
}