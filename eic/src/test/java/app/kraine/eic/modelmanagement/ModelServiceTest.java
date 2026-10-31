package app.kraine.eic.modelmanagement;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

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
import app.kraine.eic.service.ModelService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelServiceTest {

    private static final String ADMIN = "admin@example.com";

    @Mock private ModelRepoInterface modelRepository;
    @Mock private ManufacturerRepoInterface manufacturerRepository;
    @Mock private AuditService auditService;
    @Mock private ObjectProvider<ModelEquipmentLinks> equipmentLinks;

    private ModelService service;

    @BeforeEach
    void setUp() {
        service = new ModelService(modelRepository, manufacturerRepository, auditService, equipmentLinks);
    }

    private static Manufacturer manufacturer(long id, String name) {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(id);
        manufacturer.setName(name);
        return manufacturer;
    }

    private void givenModel(Model model) {
        when(modelRepository.findById(any(ModelId.class))).thenReturn(Optional.of(model));
    }

    private void givenManufacturer(Manufacturer manufacturer) {
        when(manufacturerRepository.findById(manufacturer.getId())).thenReturn(Optional.of(manufacturer));
    }

    private static ModelForm createForm(Long manufacturerId, String description) {
        ModelForm form = new ModelForm();
        form.setManufacturerId(manufacturerId);
        form.setDescription(description);
        return form;
    }

    private static ModelForm updateForm(Long manufacturerId, String description, Long originalManufacturerId,
                                        String originalDescription) {
        ModelForm form = createForm(manufacturerId, description);
        form.setOriginalManufacturerId(originalManufacturerId);
        form.setOriginalDescription(originalDescription);
        return form;
    }

    // ------------------------------------------------------------------ create

    @Test
    void createsAModelAndAuditsIt() {
        Manufacturer acme = manufacturer(1, "Acme");
        givenManufacturer(acme);
        when(modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(1L, "X100")).thenReturn(false);

        service.createModel(createForm(1L, " X100 "), ADMIN);

        ArgumentCaptor<Model> saved = ArgumentCaptor.forClass(Model.class);
        verify(modelRepository).saveAndFlush(saved.capture());
        assertEquals("X100", saved.getValue().getDescription());
        assertSame(acme, saved.getValue().getManufacturer());
        verify(auditService).record(AuditEventType.MODEL_CREATED, ADMIN, "Acme / X100", null);
    }

    @Test
    void rejectsADuplicateDescriptionForTheSameManufacturer() {
        givenManufacturer(manufacturer(1, "Acme"));
        when(modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(1L, "X100")).thenReturn(true);

        assertThrows(DuplicateModelDescriptionException.class, () -> service.createModel(createForm(1L, "X100"), ADMIN));
        verify(modelRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAManufacturerThatDoesNotExist() {
        when(manufacturerRepository.findById(9L)).thenReturn(Optional.empty());

        InvalidModelReferenceException ex = assertThrows(InvalidModelReferenceException.class,
                () -> service.createModel(createForm(9L, "X100"), ADMIN));

        assertEquals("manufacturerId", ex.getField());
    }

    @Test
    void rejectsAMissingManufacturer() {
        assertThrows(InvalidModelReferenceException.class, () -> service.createModel(createForm(null, "X100"), ADMIN));
    }

    @Test
    void treatsAKeyClashAtTheDatabaseAsADuplicate() {
        givenManufacturer(manufacturer(1, "Acme"));
        when(modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(1L, "X100")).thenReturn(false);
        when(modelRepository.saveAndFlush(any(Model.class))).thenThrow(new DataIntegrityViolationException("pk"));

        assertThrows(DuplicateModelDescriptionException.class, () -> service.createModel(createForm(1L, "X100"), ADMIN));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ update

    @Test
    void changesTheDescriptionAndAuditsIt() {
        Manufacturer acme = manufacturer(1, "Acme");
        givenModel(new Model(acme, "X100"));
        givenManufacturer(acme);
        when(modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(1L, "X200")).thenReturn(false);
        when(modelRepository.updateKey(1L, "X100", 1L, "X200")).thenReturn(1);

        service.updateModel(updateForm(1L, " X200 ", 1L, "X100"), ADMIN);

        verify(modelRepository).updateKey(1L, "X100", 1L, "X200");
        verify(auditService).record(AuditEventType.MODEL_UPDATED, ADMIN, "Acme / X200", "Was Acme / X100");
    }

    @Test
    void movesAModelToAnotherManufacturer() {
        Manufacturer acme = manufacturer(1, "Acme");
        Manufacturer globex = manufacturer(2, "Globex");
        givenModel(new Model(acme, "X100"));
        givenManufacturer(globex);
        when(modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(2L, "X100")).thenReturn(false);
        when(modelRepository.updateKey(1L, "X100", 2L, "X100")).thenReturn(1);

        service.updateModel(updateForm(2L, "X100", 1L, "X100"), ADMIN);

        verify(modelRepository).updateKey(1L, "X100", 2L, "X100");
        verify(auditService).record(AuditEventType.MODEL_UPDATED, ADMIN, "Globex / X100", "Was Acme / X100");
    }

    @Test
    void doesNothingWhenNothingChanged() {
        Manufacturer acme = manufacturer(1, "Acme");
        givenModel(new Model(acme, "X100"));
        givenManufacturer(acme);

        service.updateModel(updateForm(1L, "X100", 1L, "X100"), ADMIN);

        verify(modelRepository, never()).updateKey(any(), any(), any(), any());
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void aChangeOfLetterCaseIsNotAClashWithItself() {
        Manufacturer acme = manufacturer(1, "Acme");
        givenModel(new Model(acme, "x100"));
        givenManufacturer(acme);
        when(modelRepository.updateKey(1L, "x100", 1L, "X100")).thenReturn(1);

        service.updateModel(updateForm(1L, "X100", 1L, "x100"), ADMIN);

        verify(modelRepository, never()).existsByManufacturerIdAndDescriptionIgnoreCase(any(), any());
        verify(modelRepository).updateKey(1L, "x100", 1L, "X100");
    }

    @Test
    void refusesAChangeToAnExistingDescription() {
        Manufacturer acme = manufacturer(1, "Acme");
        givenModel(new Model(acme, "X100"));
        givenManufacturer(acme);
        when(modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(1L, "X200")).thenReturn(true);

        assertThrows(DuplicateModelDescriptionException.class,
                () -> service.updateModel(updateForm(1L, "X200", 1L, "X100"), ADMIN));
        verify(modelRepository, never()).updateKey(any(), any(), any(), any());
    }

    @Test
    void refusesToChangeAModelThatEquipmentUses() {
        Manufacturer acme = manufacturer(1, "Acme");
        givenModel(new Model(acme, "X100"));
        givenManufacturer(acme);
        when(modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(1L, "X200")).thenReturn(false);
        when(equipmentLinks.getIfAvailable()).thenReturn((manufacturerId, description) -> true);

        ModelLinkedToEquipmentException ex = assertThrows(ModelLinkedToEquipmentException.class,
                () -> service.updateModel(updateForm(1L, "X200", 1L, "X100"), ADMIN));

        assertTrue(ex.getMessage().contains("cannot be changed"));
        verify(modelRepository, never()).updateKey(any(), any(), any(), any());
    }

    @Test
    void theForeignKeyStillProtectsEquipmentWhenNoLinkCheckExists() {
        Manufacturer acme = manufacturer(1, "Acme");
        givenModel(new Model(acme, "X100"));
        givenManufacturer(acme);
        when(modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(1L, "X200")).thenReturn(false);
        when(modelRepository.updateKey(1L, "X100", 1L, "X200")).thenThrow(new DataIntegrityViolationException("fk"));

        assertThrows(ModelLinkedToEquipmentException.class,
                () -> service.updateModel(updateForm(1L, "X200", 1L, "X100"), ADMIN));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void reportsAModelThatDisappearedDuringTheChange() {
        Manufacturer acme = manufacturer(1, "Acme");
        givenModel(new Model(acme, "X100"));
        givenManufacturer(acme);
        when(modelRepository.existsByManufacturerIdAndDescriptionIgnoreCase(1L, "X200")).thenReturn(false);
        when(modelRepository.updateKey(1L, "X100", 1L, "X200")).thenReturn(0);

        assertThrows(ModelNotFoundException.class,
                () -> service.updateModel(updateForm(1L, "X200", 1L, "X100"), ADMIN));
    }

    @Test
    void rejectsAChangeToAManufacturerThatDoesNotExist() {
        givenModel(new Model(manufacturer(1, "Acme"), "X100"));
        when(manufacturerRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(InvalidModelReferenceException.class,
                () -> service.updateModel(updateForm(9L, "X100", 1L, "X100"), ADMIN));
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deletesAModelAndAuditsIt() {
        givenModel(new Model(manufacturer(1, "Acme"), "X100"));
        when(modelRepository.removeByKey(1L, "X100")).thenReturn(1);

        service.deleteModel(1L, "X100", ADMIN);

        verify(modelRepository).removeByKey(1L, "X100");
        verify(auditService).record(AuditEventType.MODEL_DELETED, ADMIN, "Acme / X100", null);
    }

    @Test
    void refusesToDeleteAModelThatEquipmentUses() {
        givenModel(new Model(manufacturer(1, "Acme"), "X100"));
        when(equipmentLinks.getIfAvailable()).thenReturn((manufacturerId, description) -> true);

        assertThrows(ModelLinkedToEquipmentException.class, () -> service.deleteModel(1L, "X100", ADMIN));
        verify(modelRepository, never()).removeByKey(any(), any());
    }

    @Test
    void theForeignKeyStillProtectsEquipmentOnDelete() {
        givenModel(new Model(manufacturer(1, "Acme"), "X100"));
        when(modelRepository.removeByKey(1L, "X100")).thenThrow(new DataIntegrityViolationException("fk"));

        assertThrows(ModelLinkedToEquipmentException.class, () -> service.deleteModel(1L, "X100", ADMIN));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void reportsAModelThatDisappearedDuringTheDelete() {
        givenModel(new Model(manufacturer(1, "Acme"), "X100"));
        when(modelRepository.removeByKey(1L, "X100")).thenReturn(0);

        assertThrows(ModelNotFoundException.class, () -> service.deleteModel(1L, "X100", ADMIN));
    }

    @Test
    void reportsAMissingModel() {
        when(modelRepository.findById(any(ModelId.class))).thenReturn(Optional.empty());

        assertThrows(ModelNotFoundException.class, () -> service.deleteModel(1L, "Nope", ADMIN));
    }

    // ------------------------------------------------------------------ read

    @Test
    void listsModelsByManufacturerThenDescription() {
        Manufacturer globex = manufacturer(2, "globex");
        Manufacturer acme = manufacturer(1, "Acme");
        when(modelRepository.findAll()).thenReturn(List.of(
                new Model(globex, "Z1"), new Model(acme, "b-series"), new Model(acme, "A-series")));

        List<ModelRow> rows = service.list();

        assertEquals(List.of("Acme / A-series", "Acme / b-series", "globex / Z1"),
                rows.stream().map(r -> r.manufacturerName() + " / " + r.description()).toList());
    }

    @Test
    void offersManufacturersByName() {
        when(manufacturerRepository.findAll()).thenReturn(List.of(manufacturer(2, "globex"), manufacturer(1, "Acme")));

        List<Option> options = service.manufacturerOptions();

        assertEquals(List.of("Acme", "globex"), options.stream().map(Option::label).toList());
    }

    @Test
    void fillsTheEditFormWithTheCurrentValues() {
        givenModel(new Model(manufacturer(1, "Acme"), "X100"));

        ModelEditData data = service.findForEdit(1L, "X100");

        assertEquals(1L, data.form().getManufacturerId());
        assertEquals("X100", data.form().getDescription());
        assertEquals(1L, data.form().getOriginalManufacturerId());
        assertEquals("X100", data.form().getOriginalDescription());
    }
}
