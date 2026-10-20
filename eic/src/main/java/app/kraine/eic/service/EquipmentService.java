package app.kraine.eic.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.equipmentManagement.CompositeKeys;
import app.kraine.eic.equipmentManagement.EquipmentRow;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentDetail;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentEditData;
import app.kraine.eic.equipmentManagement.EquipmentViews.Option;
import app.kraine.eic.exception.DuplicateSerialNumberException;
import app.kraine.eic.exception.EquipmentInUseException;
import app.kraine.eic.exception.EquipmentNotFoundException;
import app.kraine.eic.exception.InvalidEquipmentReferenceException;
import app.kraine.eic.form.EquipmentForm;
import app.kraine.eic.model.Equipment;
import app.kraine.eic.model.Location;
import app.kraine.eic.model.LocationId;
import app.kraine.eic.model.Model;
import app.kraine.eic.model.ModelId;
import app.kraine.eic.model.Property;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.EquipmentImageRepository;
import app.kraine.eic.repository.EquipmentRepoInterface;
import app.kraine.eic.repository.LocationRepoInterface;
import app.kraine.eic.repository.ModelRepoInterface;
import app.kraine.eic.repository.UserRepoInterface;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * EQM-1 to EQM-4 / UC-23 to UC-25 and US-17. Administrators manage all equipment; an Editor manages,
 * and sees, only equipment at the properties assigned to them (EQM-4, US-19). Every change is audited (UA-25).
 *
 * <p>Needs these on your repositories (see REPOSITORY-ADDITIONS.md): {@code EquipmentRepoInterface}
 * with {@code search}, {@code searchForEditor} and the serial-number checks, {@code LocationRepoInterface}
 * with {@code findByPropertyUserId}, and your equipment image repository.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentService {
 
    public static final int PAGE_SIZE = 25;
 
    private final EquipmentRepoInterface equipmentRepository;
    private final ModelRepoInterface modelRepository;
    private final LocationRepoInterface locationRepository;
    private final UserRepoInterface userRepository;
    private final EquipmentImageService imageService;
    private final AuditService auditService;
 
    // ------------------------------------------------------------------ read
 
    /** The list page. [query] looks in serial number, manufacturer, model, property and location. */
    @Transactional(readOnly = true)
    public Page<EquipmentRow> search(String query, int page, String actorEmail) {
        User actor = actorOf(actorEmail);
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        return isAdministrator(actor)
                ? equipmentRepository.search(text, pageRequest)
                : equipmentRepository.searchForEditor(text, actor.getId(), pageRequest);
    }
 
    /** US-17. */
    @Transactional(readOnly = true)
    public EquipmentDetail findDetail(Long id, String actorEmail) {
        User actor = actorOf(actorEmail);
        Equipment equipment = find(id);
        Property property = equipment.getLocation().getProperty();
        assertCanManage(actor, property);
 
        String imageName = imageService.findImageInfo(id).map(EquipmentImageRepository.ImageInfo::getImageName).orElse(null);
        User editor = property.getUser();
        return new EquipmentDetail(equipment.getId(), equipment.getSerialNumber(), equipment.getManufacturedDate(),
                equipment.getModel().getManufacturer().getName(), equipment.getModel().getDescription(),
                property.getName(), equipment.getLocation().getName(),
                editor == null ? "Not assigned" : editor.getFirstName() + " " + editor.getLastName(), imageName);
    }
 
    @Transactional(readOnly = true)
    public EquipmentEditData findForEdit(Long id, String actorEmail) {
        User actor = actorOf(actorEmail);
        Equipment equipment = find(id);
        assertCanManage(actor, equipment.getLocation().getProperty());
 
        EquipmentForm form = new EquipmentForm();
        form.setSerialNumber(equipment.getSerialNumber());
        form.setManufacturedDate(equipment.getManufacturedDate() == null ? "" : equipment.getManufacturedDate().toString());
        form.setModelKey(CompositeKeys.encode(equipment.getModel().getManufacturer().getId(),
                equipment.getModel().getDescription()));
        form.setLocationKey(CompositeKeys.encode(equipment.getLocation().getProperty().getId(),
                equipment.getLocation().getName()));
        return new EquipmentEditData(equipment.getId(), form);
    }
 
    @Transactional(readOnly = true)
    public List<Option> modelOptions() {
        return modelRepository.findAll().stream()
                .sorted(Comparator.comparing((Model m) -> m.getManufacturer().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Model::getDescription, String.CASE_INSENSITIVE_ORDER))
                .map(m -> new Option(CompositeKeys.encode(m.getManufacturer().getId(), m.getDescription()),
                        m.getManufacturer().getName() + " / " + m.getDescription()))
                .toList();
    }
 
    /** The locations the user may place equipment at: all of them for an Administrator, their own for an Editor. */
    @Transactional(readOnly = true)
    public List<Option> locationOptions(String actorEmail) {
        User actor = actorOf(actorEmail);
        List<Location> locations = isAdministrator(actor)
                ? locationRepository.findAll()
                : locationRepository.findByPropertyUserId(actor.getId());
        return locations.stream()
                .sorted(Comparator.comparing((Location l) -> l.getProperty().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Location::getName, String.CASE_INSENSITIVE_ORDER))
                .map(l -> new Option(CompositeKeys.encode(l.getProperty().getId(), l.getName()),
                        l.getProperty().getName() + " / " + l.getName()))
                .toList();
    }
 
    // ------------------------------------------------------------------ UC-23 create
 
    @Transactional
    public Equipment createEquipment(EquipmentForm form, String actorEmail) {
        User actor = actorOf(actorEmail);
        Model model = resolveModel(form.getModelKey());
        Location location = resolveLocation(form.getLocationKey());
        assertCanManage(actor, location.getProperty());
 
        String serialNumber = normalizeSerial(form.getSerialNumber());
        if (serialNumber != null && equipmentRepository.existsBySerialNumberIgnoreCase(serialNumber)) {   // step 7
            throw new DuplicateSerialNumberException(serialNumber);
        }
        YearMonth manufactured = parseMonth(form.getManufacturedDate());
 
        Equipment equipment = new Equipment();
        equipment.setSerialNumber(serialNumber);
        equipment.setManufacturedDate(manufactured);
        equipment.setModel(model);
        equipment.setLocation(location);
        Equipment saved;
        try {
            saved = equipmentRepository.saveAndFlush(equipment);                                         // step 8
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateSerialNumberException(serialNumber); // another request used it a moment ago
        }
        auditService.record(AuditEventType.EQUIPMENT_CREATED, actorEmail, target(saved), describe(model, location));
        log.info("Equipment {} created by {}", target(saved), actorEmail);
        return saved;
    }
 
    // ------------------------------------------------------------------ UC-24 update
 
    @Transactional
    public void updateEquipment(Long id, EquipmentForm form, String actorEmail) {
        User actor = actorOf(actorEmail);
        Equipment equipment = find(id);
        assertCanManage(actor, equipment.getLocation().getProperty());   // it must be theirs now...
 
        Model model = resolveModel(form.getModelKey());
        Location location = resolveLocation(form.getLocationKey());
        assertCanManage(actor, location.getProperty());                  // ...and the new location must be theirs too
 
        String serialNumber = normalizeSerial(form.getSerialNumber());
        if (serialNumber != null && equipmentRepository.existsBySerialNumberIgnoreCaseAndIdNot(serialNumber, id)) { // step 7
            throw new DuplicateSerialNumberException(serialNumber);
        }
        YearMonth manufactured = parseMonth(form.getManufacturedDate());
 
        equipment.setSerialNumber(serialNumber);
        equipment.setManufacturedDate(manufactured);
        equipment.setModel(model);
        equipment.setLocation(location);
        try {
            equipmentRepository.saveAndFlush(equipment);                                                 // step 8
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateSerialNumberException(serialNumber);
        }
        auditService.record(AuditEventType.EQUIPMENT_UPDATED, actorEmail, target(equipment), describe(model, location));
        log.info("Equipment {} updated by {}", target(equipment), actorEmail);
    }
 
    // ------------------------------------------------------------------ UC-25 delete
 
    /** Step 5: the equipment and its image, if it has one (the entity cascades the removal), are deleted. */
    @Transactional
    public void deleteEquipment(Long id, String actorEmail) {
        User actor = actorOf(actorEmail);
        Equipment equipment = find(id);
        assertCanManage(actor, equipment.getLocation().getProperty());
 
        String target = target(equipment);
        try {
            equipmentRepository.delete(equipment);
            equipmentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new EquipmentInUseException();
        }
        auditService.record(AuditEventType.EQUIPMENT_DELETED, actorEmail, target, null);
        log.info("Equipment {} deleted by {}", target, actorEmail);
    }
 
    // ------------------------------------------------------------------ helpers
 
    private Equipment find(Long id) {
        return equipmentRepository.findById(id).orElseThrow(() -> new EquipmentNotFoundException(id));
    }
 
    private User actorOf(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
    }
 
    private static boolean isAdministrator(User user) {
        return user.getRole().getRoleType() == RoleType.ADMINISTRATOR;
    }
 
    /** EQM-4: an Administrator, or the editor the property is assigned to. */
    private static void assertCanManage(User actor, Property property) {
        if (isAdministrator(actor)) {
            return;
        }
        if (property.getUser() != null && Objects.equals(property.getUser().getId(), actor.getId())) {
            return;
        }
        throw new AccessDeniedException("You can only manage equipment at the properties assigned to you.");
    }
 
    private Model resolveModel(String modelKey) {
        CompositeKeys.Key key = CompositeKeys.decode(modelKey);
        Optional<Model> model = key == null ? Optional.empty()
                : modelRepository.findById(new ModelId(key.id(), key.text()));
        return model.orElseThrow(() -> new InvalidEquipmentReferenceException("modelKey",
                key == null ? "Select a model." : "The selected model no longer exists."));
    }
 
    private Location resolveLocation(String locationKey) {
        CompositeKeys.Key key = CompositeKeys.decode(locationKey);
        Optional<Location> location = key == null ? Optional.empty()
                : locationRepository.findById(new LocationId(key.id(), key.text()));
        return location.orElseThrow(() -> new InvalidEquipmentReferenceException("locationKey",
                key == null ? "Select a location." : "The selected location no longer exists."));
    }
 
    /** A blank serial number means "none" and is stored as null, so it never clashes with another one. */
    private static String normalizeSerial(String serialNumber) {
        return serialNumber == null || serialNumber.isBlank() ? null : serialNumber.trim();
    }
 
    static YearMonth parseMonth(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        YearMonth month;
        try {
            month = YearMonth.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new InvalidEquipmentReferenceException("manufacturedDate", "Enter a valid month, for example 2026-10.");
        }
        if (month.isAfter(YearMonth.now())) {
            throw new InvalidEquipmentReferenceException("manufacturedDate", "The manufactured date cannot be in the future.");
        }
        return month;
    }
 
    private static String target(Equipment equipment) {
        return equipment.getSerialNumber() != null ? equipment.getSerialNumber() : "Equipment #" + equipment.getId();
    }
 
    private static String describe(Model model, Location location) {
        return model.getManufacturer().getName() + " / " + model.getDescription()
                + " at " + location.getProperty().getName() + " / " + location.getName();
    }
}