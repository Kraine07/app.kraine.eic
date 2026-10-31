package app.kraine.eic.manufacturer;

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
import app.kraine.eic.exception.DuplicateManufacturerNameException;
import app.kraine.eic.exception.ManufacturerInUseException;
import app.kraine.eic.exception.ManufacturerNotFoundException;
import app.kraine.eic.form.ManufacturerForm;
import app.kraine.eic.manufacturerManagement.ManufacturerEquipmentLinks;
import app.kraine.eic.manufacturerManagement.ManufacturerViews.ManufacturerEditData;
import app.kraine.eic.manufacturerManagement.ManufacturerViews.ManufacturerRow;
import app.kraine.eic.model.Manufacturer;
import app.kraine.eic.repository.ManufacturerRepoInterface;
import app.kraine.eic.service.ManufacturerService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManufacturerServiceTest {

    private static final String ADMIN = "admin@example.com";

    @Mock private ManufacturerRepoInterface manufacturerRepository;
    @Mock private AuditService auditService;
    @Mock private ObjectProvider<ManufacturerEquipmentLinks> equipmentLinks;

    private ManufacturerService service;

    @BeforeEach
    void setUp() {
        service = new ManufacturerService(manufacturerRepository, auditService, equipmentLinks);
    }

    private static Manufacturer manufacturer(long id, String name) {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(id);
        manufacturer.setName(name);
        return manufacturer;
    }

    private static ManufacturerForm form(String name) {
        ManufacturerForm form = new ManufacturerForm();
        form.setName(name);
        return form;
    }

    // ------------------------------------------------------------------ create

    @Test
    void createsAManufacturerAndAuditsIt() {
        when(manufacturerRepository.existsByNameIgnoreCase("Acme")).thenReturn(false);
        when(manufacturerRepository.saveAndFlush(any(Manufacturer.class))).thenAnswer(i -> i.getArgument(0));

        service.createManufacturer(form("  Acme "), ADMIN);

        ArgumentCaptor<Manufacturer> saved = ArgumentCaptor.forClass(Manufacturer.class);
        verify(manufacturerRepository).saveAndFlush(saved.capture());
        assertEquals("Acme", saved.getValue().getName());
        verify(auditService).record(AuditEventType.MANUFACTURER_CREATED, ADMIN, "Acme", null);
    }

    @Test
    void rejectsADuplicateNameIgnoringLetterCase() {
        when(manufacturerRepository.existsByNameIgnoreCase("acme")).thenReturn(true);

        assertThrows(DuplicateManufacturerNameException.class, () -> service.createManufacturer(form("acme"), ADMIN));
        verify(manufacturerRepository, never()).saveAndFlush(any());
    }

    @Test
    void treatsAUniqueConstraintClashAsADuplicate() {
        when(manufacturerRepository.existsByNameIgnoreCase("Acme")).thenReturn(false);
        when(manufacturerRepository.saveAndFlush(any(Manufacturer.class)))
                .thenThrow(new DataIntegrityViolationException("unique"));

        assertThrows(DuplicateManufacturerNameException.class, () -> service.createManufacturer(form("Acme"), ADMIN));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ update

    @Test
    void renamesAManufacturerAndAuditsIt() {
        Manufacturer existing = manufacturer(3, "Acme");
        when(manufacturerRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(manufacturerRepository.existsByNameIgnoreCaseAndIdNot("Acme Corp", 3L)).thenReturn(false);

        service.updateManufacturer(3L, form(" Acme Corp "), ADMIN);

        assertEquals("Acme Corp", existing.getName());
        verify(manufacturerRepository).saveAndFlush(existing);
        verify(auditService).record(AuditEventType.MANUFACTURER_UPDATED, ADMIN, "Acme Corp", "Renamed from Acme");
    }

    @Test
    void doesNothingWhenTheNameIsUnchanged() {
        when(manufacturerRepository.findById(3L)).thenReturn(Optional.of(manufacturer(3, "Acme")));

        service.updateManufacturer(3L, form("Acme"), ADMIN);

        verify(manufacturerRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void rejectsANameUsedByAnotherManufacturer() {
        when(manufacturerRepository.findById(3L)).thenReturn(Optional.of(manufacturer(3, "Acme")));
        when(manufacturerRepository.existsByNameIgnoreCaseAndIdNot("Globex", 3L)).thenReturn(true);

        assertThrows(DuplicateManufacturerNameException.class,
                () -> service.updateManufacturer(3L, form("Globex"), ADMIN));
        verify(manufacturerRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportsAMissingManufacturerOnUpdate() {
        when(manufacturerRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ManufacturerNotFoundException.class, () -> service.updateManufacturer(9L, form("X"), ADMIN));
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deletesAManufacturerAndAuditsIt() {
        Manufacturer existing = manufacturer(3, "Acme");
        when(manufacturerRepository.findById(3L)).thenReturn(Optional.of(existing));

        service.deleteManufacturer(3L, ADMIN);

        verify(manufacturerRepository).delete(existing);
        verify(manufacturerRepository).flush();
        verify(auditService).record(AuditEventType.MANUFACTURER_DELETED, ADMIN, "Acme", null);
    }

    @Test
    void refusesToDeleteAManufacturerThatIsLinked() {
        when(manufacturerRepository.findById(3L)).thenReturn(Optional.of(manufacturer(3, "Acme")));
        when(equipmentLinks.getIfAvailable()).thenReturn(manufacturerId -> true);

        assertThrows(ManufacturerInUseException.class, () -> service.deleteManufacturer(3L, ADMIN));
        verify(manufacturerRepository, never()).delete(any());
    }

    @Test
    void theForeignKeysStillProtectModelsAndEquipmentWhenNoLinkCheckExists() {
        when(manufacturerRepository.findById(3L)).thenReturn(Optional.of(manufacturer(3, "Acme")));
        doThrow(new DataIntegrityViolationException("fk")).when(manufacturerRepository).flush();

        assertThrows(ManufacturerInUseException.class, () -> service.deleteManufacturer(3L, ADMIN));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void reportsAMissingManufacturerOnDelete() {
        when(manufacturerRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ManufacturerNotFoundException.class, () -> service.deleteManufacturer(9L, ADMIN));
    }

    // ------------------------------------------------------------------ read

    @Test
    void listsManufacturersByNameIgnoringLetterCase() {
        when(manufacturerRepository.findAll()).thenReturn(
                List.of(manufacturer(1, "globex"), manufacturer(2, "Acme"), manufacturer(3, "Initech")));

        List<ManufacturerRow> rows = service.list();

        assertEquals(List.of("Acme", "globex", "Initech"), rows.stream().map(ManufacturerRow::name).toList());
    }

    @Test
    void fillsTheEditFormWithTheCurrentName() {
        when(manufacturerRepository.findById(3L)).thenReturn(Optional.of(manufacturer(3, "Acme")));

        ManufacturerEditData data = service.findForEdit(3L);

        assertEquals(3L, data.id());
        assertEquals("Acme", data.form().getName());
    }
}
