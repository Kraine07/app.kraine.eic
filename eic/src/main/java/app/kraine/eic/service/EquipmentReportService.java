package app.kraine.eic.service;


import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.kraine.eic.audit.AuditViews;
import app.kraine.eic.equipmentManagement.EquipmentRow;
import app.kraine.eic.exception.InvalidReportSelectionException;
import app.kraine.eic.model.Property;
import app.kraine.eic.model.Region;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.reportManagement.ReportViews.AllEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.EditorEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.Option;
import app.kraine.eic.reportManagement.ReportViews.PropertyEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.RegionEquipmentReport;
import app.kraine.eic.repository.EquipmentRepoInterface;
import app.kraine.eic.repository.PropertyRepoInterface;
import app.kraine.eic.repository.RegionRepoInterface;
import app.kraine.eic.repository.UserRepoInterface;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RG-1 / UC-38: the equipment at the properties assigned to an editor. RG-2 / UC-39: the equipment at one
 * property. RG-3 / UC-40: the equipment in one region. RG-4 / UC-41: all equipment. An Administrator can report on anything; an Editor only
 * on themselves, on the properties assigned to them, and on the part of a region that is theirs, the same
 * visibility rule as equipment management (EQM-4, US-19).
 *
 */


@Service
@RequiredArgsConstructor
public class EquipmentReportService {

    private final EquipmentRepoInterface equipmentRepository;
    private final UserRepoInterface userRepository;
    private final PropertyRepoInterface propertyRepository;
    private final RegionRepoInterface regionRepository;


    /** Step 3: the editors the user may select. */
    @Transactional(readOnly = true)
    public List<Option> editorOptions(String actorEmail) {
        User actor = actorOf(actorEmail);
        if (!isAdministrator(actor)) {
            return List.of(option(actor)); // an Editor can only report on themselves
        }
        return userRepository.findAllWithDetails().stream()
                .filter(user -> user.getRole().getRoleType() == RoleType.EDITOR)
                .sorted(Comparator.comparing(User::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(User::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .map(EquipmentReportService::option)
                .toList();
    }



    /**
     * Steps 6-7.
     *
     * @throws InvalidReportSelectionException the selected user does not exist or is not an editor
     * @throws AccessDeniedException           an Editor asked for a report on someone else
     */
    @Transactional(readOnly = true)
    public EditorEquipmentReport generateEditorReport(Long editorId, String actorEmail) {
        User actor = actorOf(actorEmail);
        User editor = userRepository.findById(editorId)
                .orElseThrow(() -> new InvalidReportSelectionException("The selected editor no longer exists."));
        if (editor.getRole().getRoleType() != RoleType.EDITOR) {
            throw new InvalidReportSelectionException("The selected user is not an editor.");
        }
        if (!isAdministrator(actor) && !Objects.equals(editor.getId(), actor.getId())) {
            throw new AccessDeniedException("You can only generate reports about your own equipment.");
        }

        List<EquipmentRow> rows = equipmentRepository.findRowsForEditor(editorId);
        return new EditorEquipmentReport(editor.getFirstName() + " " + editor.getLastName(), editor.getEmail(), rows,
                generatedAt());
    }



    /** UC-39 step 3: the properties the user may select. */
    @Transactional(readOnly = true)
    public List<Option> propertyOptions(String actorEmail) {
        User actor = actorOf(actorEmail);
        List<Property> properties = isAdministrator(actor)
                ? propertyRepository.findAll()
                : propertyRepository.findByUserId(actor.getId());
        return properties.stream()
                .sorted(Comparator.comparing(Property::getName, String.CASE_INSENSITIVE_ORDER))
                .map(property -> new Option(property.getId(), property.getName()))
                .toList();
    }



    /**
     * UC-39 steps 6-7.
     *
     * @throws InvalidReportSelectionException the selected property does not exist
     * @throws AccessDeniedException           an Editor asked for a property that is not assigned to them
     */
    @Transactional(readOnly = true)
    public PropertyEquipmentReport generatePropertyReport(Long propertyId, String actorEmail) {
        User actor = actorOf(actorEmail);
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new InvalidReportSelectionException("The selected property no longer exists."));
        User editor = property.getUser();
        if (!isAdministrator(actor) && (editor == null || !Objects.equals(editor.getId(), actor.getId()))) {
            throw new AccessDeniedException("You can only generate reports about the properties assigned to you.");
        }

        List<EquipmentRow> rows = equipmentRepository.findRowsForProperty(propertyId);
        return new PropertyEquipmentReport(property.getName(),
                property.getRegion() == null ? "Not set" : property.getRegion().getName(),
                editor == null ? "Not assigned" : editor.getFirstName() + " " + editor.getLastName(),
                rows, generatedAt());
    }



    /** UC-40 step 3: the regions the user may select. */
    @Transactional(readOnly = true)
    public List<Option> regionOptions(String actorEmail) {
        User actor = actorOf(actorEmail);
        Map<Long, Region> regions = new LinkedHashMap<>();
        if (isAdministrator(actor)) {
            regionRepository.findAll().forEach(region -> regions.put(region.getId(), region));
        } else {
            // An Editor can choose the regions their properties are in.
            propertyRepository.findByUserId(actor.getId()).stream()
                    .map(Property::getRegion)
                    .filter(Objects::nonNull)
                    .forEach(region -> regions.putIfAbsent(region.getId(), region));
        }
        return regions.values().stream()
                .sorted(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER))
                .map(region -> new Option(region.getId(), region.getName()))
                .toList();
    }



    /**
     * UC-40 steps 6-7. An Administrator gets every item in the region; an Editor only the items at their own
     * properties in it, and the report says so.
     *
     * @throws InvalidReportSelectionException the selected region does not exist
     * @throws AccessDeniedException           an Editor asked for a region that holds none of their properties
     */
    @Transactional(readOnly = true)
    public RegionEquipmentReport generateRegionReport(Long regionId, String actorEmail) {
        User actor = actorOf(actorEmail);
        Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> new InvalidReportSelectionException("The selected region no longer exists."));
        boolean administrator = isAdministrator(actor);
        if (!administrator && !propertyRepository.existsByRegionIdAndUserId(regionId, actor.getId())) {
            throw new AccessDeniedException("You can only generate reports about regions that contain your properties.");
        }

        List<EquipmentRow> rows = administrator
                ? equipmentRepository.findRowsForRegion(regionId)
                : equipmentRepository.findRowsForRegionAndEditor(regionId, actor.getId());
        return new RegionEquipmentReport(region.getName(), rows, generatedAt(), !administrator);
    }



    /**
     * UC-41 steps 4-5: every item the user may see. An Administrator gets all equipment; an Editor the equipment
     * at the properties assigned to them, and the report says so.
     */
    @Transactional(readOnly = true)
    public AllEquipmentReport generateAllEquipmentReport(String actorEmail) {
        User actor = actorOf(actorEmail);
        boolean administrator = isAdministrator(actor);
        List<EquipmentRow> rows = administrator
                ? equipmentRepository.findAllRows()
                : equipmentRepository.findRowsForEditor(actor.getId());
        return new AllEquipmentReport(rows, generatedAt(), !administrator);
    }



    // ------------------------------------------------------------------ helpers

    private static String generatedAt() {
        return AuditViews.formatTime(Instant.now()) + " UTC";
    }



    private User actorOf(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
    }



    private static boolean isAdministrator(User user) {
        return user.getRole().getRoleType() == RoleType.ADMINISTRATOR;
    }



    private static Option option(User user) {
        return new Option(user.getId(), user.getFirstName() + " " + user.getLastName() + " (" + user.getEmail() + ")"
                + (Boolean.TRUE.equals(user.getIsSuspended()) ? " - suspended" : ""));
    }
}