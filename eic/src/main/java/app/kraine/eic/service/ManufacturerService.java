package app.kraine.eic.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.exception.DuplicateManufacturerNameException;
import app.kraine.eic.exception.ManufacturerInUseException;
import app.kraine.eic.exception.ManufacturerNotFoundException;
import app.kraine.eic.form.ManufacturerForm;
import app.kraine.eic.manufacturerManagement.ManufacturerEquipmentLinks;
import app.kraine.eic.manufacturerManagement.ManufacturerViews.ManufacturerEditData;
import app.kraine.eic.manufacturerManagement.ManufacturerViews.ManufacturerRow;
import app.kraine.eic.model.Manufacturer;
import app.kraine.eic.repository.ManufacturerRepoInterface;

import java.util.Comparator;
import java.util.List;
 
/**
 * MAN-2 to MAN-4 / UC-08 to UC-10: creating, renaming and deleting manufacturers. Every change is
 * audited with the acting Administrator (UA-25).
 *
 * <p>Written against these assumptions: {@code Manufacturer} is an entity with {@code getId()},
 * {@code getName()} and {@code setName(String)}, and {@code ManufacturerRepoInterface} is a
 * {@code JpaRepository<Manufacturer, Long>} with {@code existsByNameIgnoreCase(String)} and
 * {@code existsByNameIgnoreCaseAndIdNot(String, Long)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManufacturerService {

    private final ManufacturerRepoInterface manufacturerRepository;
    private final AuditService auditService;
    private final ObjectProvider<ManufacturerEquipmentLinks> equipmentLinks;

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<ManufacturerRow> list() {
        return manufacturerRepository.findAll().stream()
                .sorted(Comparator.comparing(Manufacturer::getName, String.CASE_INSENSITIVE_ORDER))
                .map(m -> new ManufacturerRow(m.getId(), m.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ManufacturerEditData findForEdit(Long id) {
        Manufacturer manufacturer = find(id);
        ManufacturerForm form = new ManufacturerForm();
        form.setName(manufacturer.getName());
        return new ManufacturerEditData(manufacturer.getId(), form);
    }

    // ------------------------------------------------------------------ UC-08 create

    @Transactional
    public Manufacturer createManufacturer(ManufacturerForm form, String actor) {
        String name = form.getName().trim();
        if (manufacturerRepository.existsByNameIgnoreCase(name)) {                // step 7
            throw new DuplicateManufacturerNameException(name);
        }
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName(name);
        Manufacturer saved;
        try {
            saved = manufacturerRepository.saveAndFlush(manufacturer);            // step 8
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateManufacturerNameException(name); // another request used the name a moment ago
        }
        auditService.record(AuditEventType.MANUFACTURER_CREATED, actor, saved.getName(), null);
        log.info("Manufacturer {} created by {}", saved.getName(), actor);
        return saved;
    }

    // ------------------------------------------------------------------ UC-09 update

    @Transactional
    public void updateManufacturer(Long id, ManufacturerForm form, String actor) {
        Manufacturer manufacturer = find(id);
        String oldName = manufacturer.getName();
        String newName = form.getName().trim();
        if (oldName.equals(newName)) {
            return; // nothing to change
        }
        // The check leaves out this manufacturer itself, so a change of letter case only is fine.
        if (manufacturerRepository.existsByNameIgnoreCaseAndIdNot(newName, id)) { // step 7
            throw new DuplicateManufacturerNameException(newName);
        }
        manufacturer.setName(newName);
        try {
            manufacturerRepository.saveAndFlush(manufacturer);                    // step 8
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateManufacturerNameException(newName);
        }
        auditService.record(AuditEventType.MANUFACTURER_UPDATED, actor, newName, "Renamed from " + oldName);
        log.info("Manufacturer {} renamed to {} by {}", oldName, newName, actor);
    }

    // ------------------------------------------------------------------ UC-10 delete

    @Transactional
    public void deleteManufacturer(Long id, String actor) {
        Manufacturer manufacturer = find(id);
        ManufacturerEquipmentLinks links = equipmentLinks.getIfAvailable();
        if (links != null && links.isLinked(id)) {                                // step 6
            throw new ManufacturerInUseException();
        }
        String name = manufacturer.getName();
        try {
            manufacturerRepository.delete(manufacturer);                          // step 7
            manufacturerRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // Without a ManufacturerEquipmentLinks bean, the foreign keys from models and equipment stop this.
            throw new ManufacturerInUseException();
        }
        auditService.record(AuditEventType.MANUFACTURER_DELETED, actor, name, null);
        log.info("Manufacturer {} deleted by {}", name, actor);
    }

    private Manufacturer find(Long id) {
        return manufacturerRepository.findById(id).orElseThrow(() -> new ManufacturerNotFoundException(id));
    }
}
