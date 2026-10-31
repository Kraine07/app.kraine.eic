package app.kraine.eic.location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

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
import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.propertyManagement.PropertyViews.Option;
import app.kraine.eic.repository.LocationRepoInterface;
import app.kraine.eic.repository.PropertyRepoInterface;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.service.LocationService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    private static final String ADMIN = "admin@example.com";
    private static final String EDITOR = "editor@example.com";

    @Mock private LocationRepoInterface locationRepository;
    @Mock private PropertyRepoInterface propertyRepository;
    @Mock private UserRepoInterface userRepository;
    @Mock private AuditService auditService;
    @Mock private ObjectProvider<LocationEquipmentLinks> equipmentLinks;

    private LocationService service;

    @BeforeEach
    void setUp() {
        service = new LocationService(locationRepository, propertyRepository, userRepository, auditService,
                equipmentLinks);
    }

    private static User user(long id, String email, RoleType type) {
        Role role = new Role();
        role.setRoleType(type);
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private User givenAdministrator() {
        User admin = user(1, ADMIN, RoleType.ADMINISTRATOR);
        when(userRepository.findByEmailIgnoreCase(ADMIN)).thenReturn(Optional.of(admin));
        return admin;
    }

    private User givenEditor() {
        User editor = user(2, EDITOR, RoleType.EDITOR);
        when(userRepository.findByEmailIgnoreCase(EDITOR)).thenReturn(Optional.of(editor));
        return editor;
    }

    private static Property property(long id, String name, User editor) {
        Property property = new Property();
        property.setId(id);
        property.setName(name);
        property.setUser(editor);
        return property;
    }

    private static Location location(Property property, String name) {
        Location location = new Location(property, name);
        property.getLocations().add(location);
        return location;
    }

    private void givenLocation(Location location) {
        when(locationRepository.findById(any(LocationId.class))).thenReturn(Optional.of(location));
    }

    private static LocationForm form(Long propertyId, String name, String originalName) {
        LocationForm form = new LocationForm();
        form.setPropertyId(propertyId);
        form.setName(name);
        form.setOriginalName(originalName);
        return form;
    }

    // ------------------------------------------------------------------ create

    @Test
    void anAdministratorCreatesALocationInAnyProperty() {
        givenAdministrator();
        Property north = property(10, "North Site", null);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(north));
        when(locationRepository.existsByPropertyIdAndNameIgnoreCase(10L, "Store Room")).thenReturn(false);

        service.createLocation(form(10L, " Store Room ", null), ADMIN);

        ArgumentCaptor<Location> saved = ArgumentCaptor.forClass(Location.class);
        verify(locationRepository).saveAndFlush(saved.capture());
        assertEquals("Store Room", saved.getValue().getName());
        assertSame(north, saved.getValue().getProperty());
        verify(auditService).record(AuditEventType.LOCATION_CREATED, ADMIN, "North Site / Store Room", null);
    }

    @Test
    void anEditorCreatesALocationInTheirOwnProperty() {
        User editor = givenEditor();
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property(10, "North Site", editor)));
        when(locationRepository.existsByPropertyIdAndNameIgnoreCase(10L, "Store Room")).thenReturn(false);

        service.createLocation(form(10L, "Store Room", null), EDITOR);

        verify(locationRepository).saveAndFlush(any(Location.class));
    }

    @Test
    void anEditorCannotCreateALocationInSomeoneElsesProperty() {
        givenEditor();
        when(propertyRepository.findById(10L))
                .thenReturn(Optional.of(property(10, "North Site", user(3, "other@example.com", RoleType.EDITOR))));

        assertThrows(AccessDeniedException.class, () -> service.createLocation(form(10L, "Store Room", null), EDITOR));
        verify(locationRepository, never()).saveAndFlush(any());
    }

    @Test
    void anEditorCannotCreateALocationInAPropertyWithoutAnEditor() {
        givenEditor();
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property(10, "North Site", null)));

        assertThrows(AccessDeniedException.class, () -> service.createLocation(form(10L, "Store Room", null), EDITOR));
    }

    @Test
    void rejectsADuplicateNameInTheSameProperty() {
        givenAdministrator();
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property(10, "North Site", null)));
        when(locationRepository.existsByPropertyIdAndNameIgnoreCase(10L, "Store Room")).thenReturn(true);

        assertThrows(DuplicateLocationNameException.class,
                () -> service.createLocation(form(10L, "Store Room", null), ADMIN));
        verify(locationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAPropertyThatDoesNotExist() {
        givenAdministrator();
        when(propertyRepository.findById(9L)).thenReturn(Optional.empty());

        InvalidLocationReferenceException ex = assertThrows(InvalidLocationReferenceException.class,
                () -> service.createLocation(form(9L, "Store Room", null), ADMIN));

        assertEquals("propertyId", ex.getField());
    }

    // ------------------------------------------------------------------ update

    @Test
    void renamesALocationAndAuditsIt() {
        givenAdministrator();
        givenLocation(location(property(10, "North Site", null), "Store"));
        when(locationRepository.existsByPropertyIdAndNameIgnoreCase(10L, "Storage")).thenReturn(false);
        when(locationRepository.rename(10L, "Store", "Storage")).thenReturn(1);

        service.updateLocation(form(10L, " Storage ", "Store"), ADMIN);

        verify(locationRepository).rename(10L, "Store", "Storage");
        verify(auditService).record(AuditEventType.LOCATION_UPDATED, ADMIN, "North Site / Storage", "Renamed from Store");
    }

    @Test
    void doesNothingWhenTheNameIsUnchanged() {
        givenAdministrator();
        givenLocation(location(property(10, "North Site", null), "Store"));

        service.updateLocation(form(10L, "Store", "Store"), ADMIN);

        verify(locationRepository, never()).rename(any(), any(), any());
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void aChangeOfLetterCaseIsNotAClashWithItself() {
        givenAdministrator();
        givenLocation(location(property(10, "North Site", null), "store"));
        when(locationRepository.rename(10L, "store", "Store")).thenReturn(1);

        service.updateLocation(form(10L, "Store", "store"), ADMIN);

        verify(locationRepository, never()).existsByPropertyIdAndNameIgnoreCase(any(), any());
        verify(locationRepository).rename(10L, "store", "Store");
    }

    @Test
    void refusesARenameToAnExistingName() {
        givenAdministrator();
        givenLocation(location(property(10, "North Site", null), "Store"));
        when(locationRepository.existsByPropertyIdAndNameIgnoreCase(10L, "Yard")).thenReturn(true);

        assertThrows(DuplicateLocationNameException.class,
                () -> service.updateLocation(form(10L, "Yard", "Store"), ADMIN));
        verify(locationRepository, never()).rename(any(), any(), any());
    }

    @Test
    void refusesToRenameALocationThatHasEquipment() {
        givenAdministrator();
        givenLocation(location(property(10, "North Site", null), "Store"));
        when(locationRepository.existsByPropertyIdAndNameIgnoreCase(10L, "Storage")).thenReturn(false);
        when(equipmentLinks.getIfAvailable()).thenReturn((propertyId, name) -> true);

        LocationLinkedToEquipmentException ex = assertThrows(LocationLinkedToEquipmentException.class,
                () -> service.updateLocation(form(10L, "Storage", "Store"), ADMIN));

        assertTrue(ex.getMessage().contains("cannot be renamed"));
        verify(locationRepository, never()).rename(any(), any(), any());
    }

    @Test
    void theForeignKeyStillProtectsEquipmentWhenNoLinkCheckExists() {
        givenAdministrator();
        givenLocation(location(property(10, "North Site", null), "Store"));
        when(locationRepository.existsByPropertyIdAndNameIgnoreCase(10L, "Storage")).thenReturn(false);
        when(locationRepository.rename(10L, "Store", "Storage")).thenThrow(new DataIntegrityViolationException("fk"));

        assertThrows(LocationLinkedToEquipmentException.class,
                () -> service.updateLocation(form(10L, "Storage", "Store"), ADMIN));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void reportsALocationThatDisappearedDuringTheRename() {
        givenAdministrator();
        givenLocation(location(property(10, "North Site", null), "Store"));
        when(locationRepository.existsByPropertyIdAndNameIgnoreCase(10L, "Storage")).thenReturn(false);
        when(locationRepository.rename(10L, "Store", "Storage")).thenReturn(0);

        assertThrows(LocationNotFoundException.class,
                () -> service.updateLocation(form(10L, "Storage", "Store"), ADMIN));
    }

    @Test
    void anEditorCannotRenameALocationOfAnotherEditor() {
        givenEditor();
        givenLocation(location(property(10, "North Site", user(3, "other@example.com", RoleType.EDITOR)), "Store"));

        assertThrows(AccessDeniedException.class,
                () -> service.updateLocation(form(10L, "Storage", "Store"), EDITOR));
        verify(locationRepository, never()).rename(any(), any(), any());
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deletesALocationByRemovingItFromItsPropertyAndAuditsIt() {
        givenAdministrator();
        Property north = property(10, "North Site", null);
        givenLocation(location(north, "Store"));
        location(north, "Yard");

        service.deleteLocation(10L, "Store", ADMIN);

        assertEquals(List.of("Yard"), north.getLocations().stream().map(Location::getName).toList());
        verify(locationRepository).flush();
        verify(auditService).record(AuditEventType.LOCATION_DELETED, ADMIN, "North Site / Store", null);
    }

    @Test
    void refusesToDeleteALocationThatHasEquipment() {
        givenAdministrator();
        Property north = property(10, "North Site", null);
        givenLocation(location(north, "Store"));
        when(equipmentLinks.getIfAvailable()).thenReturn((propertyId, name) -> true);

        assertThrows(LocationLinkedToEquipmentException.class, () -> service.deleteLocation(10L, "Store", ADMIN));
        assertEquals(1, north.getLocations().size());
        verify(locationRepository, never()).flush();
    }

    @Test
    void theForeignKeyStillProtectsEquipmentOnDelete() {
        givenAdministrator();
        givenLocation(location(property(10, "North Site", null), "Store"));
        doThrow(new DataIntegrityViolationException("fk")).when(locationRepository).flush();

        assertThrows(LocationLinkedToEquipmentException.class, () -> service.deleteLocation(10L, "Store", ADMIN));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void anEditorCannotDeleteALocationOfAnotherEditor() {
        givenEditor();
        givenLocation(location(property(10, "North Site", user(3, "other@example.com", RoleType.EDITOR)), "Store"));

        assertThrows(AccessDeniedException.class, () -> service.deleteLocation(10L, "Store", EDITOR));
        verify(locationRepository, never()).flush();
    }

    @Test
    void reportsAMissingLocation() {
        givenAdministrator();
        when(locationRepository.findById(any(LocationId.class))).thenReturn(Optional.empty());

        assertThrows(LocationNotFoundException.class, () -> service.deleteLocation(10L, "Nope", ADMIN));
    }

    // ------------------------------------------------------------------ read

    @Test
    void anAdministratorSeesEveryLocationSortedByPropertyThenName() {
        givenAdministrator();
        Property south = property(2, "South Site", null);
        Property north = property(1, "north site", null);
        when(locationRepository.findAll()).thenReturn(List.of(
                location(south, "Yard"), location(north, "store"), location(north, "Attic")));

        List<LocationRow> rows = service.list(ADMIN);

        assertEquals(List.of("north site / Attic", "north site / store", "South Site / Yard"),
                rows.stream().map(r -> r.propertyName() + " / " + r.name()).toList());
    }

    @Test
    void anEditorOnlySeesTheLocationsOfTheirOwnProperties() {
        User editor = givenEditor();
        when(locationRepository.findByPropertyUserId(2L))
                .thenReturn(List.of(location(property(1, "North Site", editor), "Store")));

        List<LocationRow> rows = service.list(EDITOR);

        assertEquals(1, rows.size());
        verify(locationRepository, never()).findAll();
    }

    @Test
    void offersAnAdministratorAllPropertiesAndAnEditorOnlyTheirs() {
        givenAdministrator();
        givenEditor();
        when(propertyRepository.findAll()).thenReturn(List.of(property(1, "B Site", null), property(2, "A Site", null)));
        when(propertyRepository.findByUserId(2L)).thenReturn(List.of(property(2, "A Site", null)));

        List<Option> forAdmin = service.propertyOptions(ADMIN);
        List<Option> forEditor = service.propertyOptions(EDITOR);

        assertEquals(List.of("A Site", "B Site"), forAdmin.stream().map(Option::label).toList());
        assertEquals(List.of("A Site"), forEditor.stream().map(Option::label).toList());
    }

    @Test
    void fillsTheEditFormWithTheCurrentName() {
        givenAdministrator();
        givenLocation(location(property(10, "North Site", null), "Store"));

        LocationEditData data = service.findForEdit(10L, "Store", ADMIN);

        assertEquals("North Site", data.propertyName());
        assertEquals("Store", data.form().getName());
        assertEquals("Store", data.form().getOriginalName());
        assertEquals(10L, data.form().getPropertyId());
    }
}
