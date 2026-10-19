package app.kraine.eic.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.exception.DuplicateModelDescriptionException;
import app.kraine.eic.exception.InvalidModelReferenceException;
import app.kraine.eic.exception.ModelLinkedToEquipmentException;
import app.kraine.eic.exception.ModelNotFoundException;
import app.kraine.eic.form.ModelForm;
import app.kraine.eic.model.Manufacturer;
import app.kraine.eic.model.Model;
import app.kraine.eic.model.ModelId;
import app.kraine.eic.modelManagement.ModelEquipmentLinks;
import app.kraine.eic.modelManagement.ModelViews.ModelEditData;
import app.kraine.eic.modelManagement.ModelViews.ModelRow;
import app.kraine.eic.modelManagement.ModelViews.Option;
import app.kraine.eic.repository.ManufacturerRepoInterface;
import app.kraine.eic.repository.ModelRepoInterface;

import java.util.Comparator;
import java.util.List;

/**
 * MOD-2 to MOD-4 / UC-11 to UC-13: creating, changing and deleting equipment models. Administrators only.
 * Every change is audited with the acting Administrator (UA-25).
 *
 * <p>A model is identified by its manufacturer and description (a composite key), which shapes two things:
 * <ul>
 *   <li>Changing either one changes the primary key. It is done with one UPDATE statement, and it only
 *       works while no equipment uses the model, because equipment references that key.</li>
 *   <li>Deleting is one DELETE statement by key. That works whether or not {@code Manufacturer} keeps a
 *       cascading list of its models, which could otherwise undo the delete.</li>
 * </ul>
 *
 * <p>Needs these on your repositories:
 * <ul>
 *   <li>{@code ModelRepoInterface extends JpaRepository<Model, ModelId>}:
 *       {@code existsByManufacturerIdAndDescriptionIgnoreCase(Long, String)},
 *       {@code updateKey(Long, String, Long, String)} and {@code removeByKey(Long, String)}</li>
 *   <li>{@code ManufacturerRepoInterface}: {@code findAll()} and {@code findById(Long)}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

    private final ModelRepoInterface modelRepository;
    private final ManufacturerRepoInterface manufacturerRepository;
    private final AuditService auditService;
    private final ObjectProvider<ModelEquipmentLinks> equipmentLinks;

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<ModelRow> list() {
        return modelRepository.findAll().stream()
                .sorted(Comparator.comparing((Model m) -> m.getManufacturer().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Model::getDescription, String.CASE_INSENSITIVE_ORDER))
                .map(m -> new ModelRow(m.getManufacturer().getId(), m.getManufacturer().getName(), m.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Option> manufacturerOptions() {
        return manufacturerRepository.findAll().stream()
                .sorted(Comparator.comparing(Manufacturer::getName, String.CASE_INSENSITIVE_ORDER))
                .map(m -> new Option(m.getId(), m.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ModelEditData findForEdit(Long manufacturerId, String description) {
        Model model = find(manufacturerId, description);
        ModelForm form = new ModelForm();
        form.setManufacturerId(manufacturerId);
        form.setDescription(model.getDescription());
        form.setOriginalManufacturerId(manufacturerId);
        form.setOriginalDescription(model.getDescription());
        return new ModelEditData(form);
    }

    // ------------------------------------------------------------------ UC-11 create

    @Transactional
    public void createModel(ModelForm form, String actor) {
        Manufacturer manufacturer = findManufacturer(form.getManufacturerId());
        String description = form.getDescription().trim();
        if (modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(manufacturer.getId(), description)) { // step 7
            throw new DuplicateModelDescriptionException(description, manufacturer.getName());
        }
        try {
            modelRepository.saveAndFlush(new Model(manufacturer, description));                                // step 8
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateModelDescriptionException(description, manufacturer.getName());
        }
        auditService.record(AuditEventType.MODEL_CREATED, actor, label(manufacturer.getName(), description), null);
        log.info("Model {} created by {}", label(manufacturer.getName(), description), actor);
    }

    // ------------------------------------------------------------------ UC-12 update

    @Transactional
    public void updateModel(ModelForm form, String actor) {
        Model model = find(form.getOriginalManufacturerId(), form.getOriginalDescription());
        Long oldManufacturerId = model.getManufacturer().getId();
        String oldManufacturerName = model.getManufacturer().getName();
        String oldDescription = model.getDescription();

        Manufacturer manufacturer = findManufacturer(form.getManufacturerId());
        String newDescription = form.getDescription().trim();
        boolean sameManufacturer = oldManufacturerId.equals(manufacturer.getId());
        if (sameManufacturer && oldDescription.equals(newDescription)) {
            return; // nothing to change
        }
        // A change of letter case only ("x100" to "X100") is not a clash with itself.
        boolean caseOnlyChange = sameManufacturer && oldDescription.equalsIgnoreCase(newDescription);
        if (!caseOnlyChange
                && modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(manufacturer.getId(), newDescription)) {
            throw new DuplicateModelDescriptionException(newDescription, manufacturer.getName());
        }
        ModelEquipmentLinks links = equipmentLinks.getIfAvailable();
        if (links != null && links.isLinkedToEquipment(oldManufacturerId, oldDescription)) {
            throw ModelLinkedToEquipmentException.forChange();
        }

        int updated;
        try {
            updated = modelRepository.updateKey(oldManufacturerId, oldDescription, manufacturer.getId(), newDescription); // step 8
        } catch (DataIntegrityViolationException ex) {
            // Without a ModelEquipmentLinks bean, the foreign key from equipment is what stops this.
            throw ModelLinkedToEquipmentException.forChange();
        }
        if (updated == 0) {
            throw new ModelNotFoundException(oldManufacturerId, oldDescription);
        }
        auditService.record(AuditEventType.MODEL_UPDATED, actor, label(manufacturer.getName(), newDescription),
                "Was " + label(oldManufacturerName, oldDescription));
        log.info("Model {} changed to {} by {}", label(oldManufacturerName, oldDescription),
                label(manufacturer.getName(), newDescription), actor);
    }

    // ------------------------------------------------------------------ UC-13 delete

    @Transactional
    public void deleteModel(Long manufacturerId, String description, String actor) {
        Model model = find(manufacturerId, description);
        String manufacturerName = model.getManufacturer().getName();
        String storedDescription = model.getDescription();

        ModelEquipmentLinks links = equipmentLinks.getIfAvailable();
        if (links != null && links.isLinkedToEquipment(manufacturerId, storedDescription)) {                  // step 5
            throw ModelLinkedToEquipmentException.forDelete();
        }
        int removed;
        try {
            removed = modelRepository.removeByKey(manufacturerId, storedDescription);                          // step 6
        } catch (DataIntegrityViolationException ex) {
            throw ModelLinkedToEquipmentException.forDelete();
        }
        if (removed == 0) {
            throw new ModelNotFoundException(manufacturerId, storedDescription);
        }
        auditService.record(AuditEventType.MODEL_DELETED, actor, label(manufacturerName, storedDescription), null);
        log.info("Model {} deleted by {}", label(manufacturerName, storedDescription), actor);
    }

    // ------------------------------------------------------------------ helpers

    private Model find(Long manufacturerId, String description) {
        return modelRepository.findById(new ModelId(manufacturerId, description))
                .orElseThrow(() -> new ModelNotFoundException(manufacturerId, description));
    }

    private Manufacturer findManufacturer(Long manufacturerId) {
        if (manufacturerId == null) {
            throw new InvalidModelReferenceException("manufacturerId", "Select a manufacturer.");
        }
        return manufacturerRepository.findById(manufacturerId).orElseThrow(() ->
                new InvalidModelReferenceException("manufacturerId", "The selected manufacturer no longer exists."));
    }

    private static String label(String manufacturerName, String description) {
        return manufacturerName + " / " + description;
    }
}
