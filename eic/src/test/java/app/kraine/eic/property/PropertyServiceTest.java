package app.kraine.eic.property;


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
import app.kraine.eic.exception.DuplicatePropertyNameException;
import app.kraine.eic.exception.InvalidPropertyReferenceException;
import app.kraine.eic.exception.PropertyLinkedToEquipmentException;
import app.kraine.eic.exception.PropertyNotFoundException;
import app.kraine.eic.form.PropertyForm;
import app.kraine.eic.model.Location;
import app.kraine.eic.model.Property;
import app.kraine.eic.model.Region;
import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.propertyManagement.PropertyEquipmentLinks;
import app.kraine.eic.propertyManagement.PropertyViews.Option;
import app.kraine.eic.propertyManagement.PropertyViews.PropertyRow;
import app.kraine.eic.repository.PropertyRepoInterface;
import app.kraine.eic.repository.RegionRepoInterface;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.service.PropertyService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    private static final String ADMIN = "admin@example.com";

    @Mock private PropertyRepoInterface propertyRepository;
    @Mock private RegionRepoInterface regionRepository;
    @Mock private UserRepoInterface userRepository;
    @Mock private AuditService auditService;
    @Mock private ObjectProvider<PropertyEquipmentLinks> equipmentLinks;

    private PropertyService service;

    @BeforeEach
    void setUp() {
        service = new PropertyService(propertyRepository, regionRepository, userRepository, auditService,
                equipmentLinks);
    }

    private static Region region(long id, String name) {
        Region region = new Region();
        region.setId(id);
        region.setName(name);
        return region;
    }

    private static User user(long id, RoleType type, boolean suspended) {
        Role role = new Role();
        role.setRoleType(type);
        User user = new User();
        user.setId(id);
        user.setFirstName("Ana");
        user.setLastName("Silva");
        user.setEmail("ana@example.com");
        user.setRole(role);
        user.setIsSuspended(suspended);
        return user;
    }

    private static Property property(long id, String name, Region region, User editor, int locations) {
        Property property = new Property();
        property.setId(id);
        property.setName(name);
        property.setRegion(region);
        property.setUser(editor);
        for (int i = 0; i < locations; i++) {
            property.getLocations().add(new Location());
        }
        return property;
    }

    private static PropertyForm form(String name, Long regionId, Long editorId) {
        PropertyForm form = new PropertyForm();
        form.setName(name);
        form.setRegionId(regionId);
        form.setEditorId(editorId);
        return form;
    }

    // ------------------------------------------------------------------ create

    @Test
    void createsAPropertyWithItsRegionAndEditorAndAuditsIt() {
        Region north = region(1, "North");
        User editor = user(5, RoleType.EDITOR, false);
        when(propertyRepository.existsByNameIgnoreCase("North Site")).thenReturn(false);
        when(regionRepository.findById(1L)).thenReturn(Optional.of(north));
        when(userRepository.findById(5L)).thenReturn(Optional.of(editor));
        when(propertyRepository.saveAndFlush(any(Property.class))).thenAnswer(i -> i.getArgument(0));

        service.createProperty(form(" North Site ", 1L, 5L), ADMIN);

        ArgumentCaptor<Property> saved = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).saveAndFlush(saved.capture());
        assertEquals("North Site", saved.getValue().getName());
        assertSame(north, saved.getValue().getRegion());
        assertSame(editor, saved.getValue().getUser());
        verify(auditService).record(AuditEventType.PROPERTY_CREATED, ADMIN, "North Site",
                "Region North, editor ana@example.com");
    }

    @Test
    void createsAPropertyWithoutAnEditor() {
        when(propertyRepository.existsByNameIgnoreCase("North Site")).thenReturn(false);
        when(regionRepository.findById(1L)).thenReturn(Optional.of(region(1, "North")));
        when(propertyRepository.saveAndFlush(any(Property.class))).thenAnswer(i -> i.getArgument(0));

        Property created = service.createProperty(form("North Site", 1L, null), ADMIN);

        assertNull(created.getUser());
        verify(auditService).record(AuditEventType.PROPERTY_CREATED, ADMIN, "North Site", "Region North");
    }

    @Test
    void rejectsADuplicateNameWithoutSaving() {
        when(propertyRepository.existsByNameIgnoreCase("North Site")).thenReturn(true);

        assertThrows(DuplicatePropertyNameException.class,
                () -> service.createProperty(form("North Site", 1L, null), ADMIN));
        verify(propertyRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsARegionThatDoesNotExist() {
        when(propertyRepository.existsByNameIgnoreCase("North Site")).thenReturn(false);
        when(regionRepository.findById(9L)).thenReturn(Optional.empty());

        InvalidPropertyReferenceException ex = assertThrows(InvalidPropertyReferenceException.class,
                () -> service.createProperty(form("North Site", 9L, null), ADMIN));

        assertEquals("regionId", ex.getField());
    }

    @Test
    void rejectsAnAdministratorAsTheEditor() {
        when(propertyRepository.existsByNameIgnoreCase("North Site")).thenReturn(false);
        when(regionRepository.findById(1L)).thenReturn(Optional.of(region(1, "North")));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2, RoleType.ADMINISTRATOR, false)));

        InvalidPropertyReferenceException ex = assertThrows(InvalidPropertyReferenceException.class,
                () -> service.createProperty(form("North Site", 1L, 2L), ADMIN));

        assertEquals("editorId", ex.getField());
        verify(propertyRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsASuspendedEditorWhenCreating() {
        when(propertyRepository.existsByNameIgnoreCase("North Site")).thenReturn(false);
        when(regionRepository.findById(1L)).thenReturn(Optional.of(region(1, "North")));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5, RoleType.EDITOR, true)));

        assertThrows(InvalidPropertyReferenceException.class,
                () -> service.createProperty(form("North Site", 1L, 5L), ADMIN));
    }

    // ------------------------------------------------------------------ update

    @Test
    void updatesAPropertyAndAuditsIt() {
        Region south = region(2, "South");
        Property existing = property(9, "Old Name", region(1, "North"), null, 0);
        when(propertyRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(propertyRepository.existsByNameIgnoreCaseAndIdNot("New Name", 9L)).thenReturn(false);
        when(regionRepository.findById(2L)).thenReturn(Optional.of(south));

        service.updateProperty(9L, form("New Name", 2L, null), ADMIN);

        assertEquals("New Name", existing.getName());
        assertSame(south, existing.getRegion());
        verify(propertyRepository).saveAndFlush(existing);
        verify(auditService).record(AuditEventType.PROPERTY_UPDATED, ADMIN, "New Name", "Region South");
    }

    @Test
    void keepsAnEditorWhoHasSinceBeenSuspended() {
        User suspended = user(5, RoleType.EDITOR, true);
        Property existing = property(9, "North Site", region(1, "North"), suspended, 0);
        when(propertyRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(propertyRepository.existsByNameIgnoreCaseAndIdNot("North Site", 9L)).thenReturn(false);
        when(regionRepository.findById(1L)).thenReturn(Optional.of(region(1, "North")));
        when(userRepository.findById(5L)).thenReturn(Optional.of(suspended));

        service.updateProperty(9L, form("North Site", 1L, 5L), ADMIN);

        assertSame(suspended, existing.getUser());
        verify(propertyRepository).saveAndFlush(existing);
    }

    @Test
    void refusesToSwitchToASuspendedEditor() {
        Property existing = property(9, "North Site", region(1, "North"), user(5, RoleType.EDITOR, false), 0);
        when(propertyRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(propertyRepository.existsByNameIgnoreCaseAndIdNot("North Site", 9L)).thenReturn(false);
        when(regionRepository.findById(1L)).thenReturn(Optional.of(region(1, "North")));
        when(userRepository.findById(6L)).thenReturn(Optional.of(user(6, RoleType.EDITOR, true)));

        assertThrows(InvalidPropertyReferenceException.class,
                () -> service.updateProperty(9L, form("North Site", 1L, 6L), ADMIN));
        verify(propertyRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsANameUsedByAnotherProperty() {
        when(propertyRepository.findById(9L)).thenReturn(Optional.of(property(9, "Old", region(1, "North"), null, 0)));
        when(propertyRepository.existsByNameIgnoreCaseAndIdNot("Taken", 9L)).thenReturn(true);

        assertThrows(DuplicatePropertyNameException.class,
                () -> service.updateProperty(9L, form("Taken", 1L, null), ADMIN));
        verify(propertyRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ delete

    @Test
    void refusesToDeleteAPropertyThatHasEquipment() {
        when(propertyRepository.findById(9L)).thenReturn(Optional.of(property(9, "North Site", null, null, 2)));
        when(equipmentLinks.getIfAvailable()).thenReturn(propertyId -> true);

        assertThrows(PropertyLinkedToEquipmentException.class, () -> service.deleteProperty(9L, ADMIN));
        verify(propertyRepository, never()).delete(any());
    }

    @Test
    void theForeignKeyStillProtectsEquipmentWhenNoLinkCheckExists() {
        Property existing = property(9, "North Site", null, null, 2);
        when(propertyRepository.findById(9L)).thenReturn(Optional.of(existing));
        doThrow(new DataIntegrityViolationException("fk")).when(propertyRepository).flush();

        assertThrows(PropertyLinkedToEquipmentException.class, () -> service.deleteProperty(9L, ADMIN));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void deletesAPropertyAndItsLocationsAndAuditsIt() {
        Property existing = property(9, "North Site", null, null, 2);
        when(propertyRepository.findById(9L)).thenReturn(Optional.of(existing));

        service.deleteProperty(9L, ADMIN);

        verify(propertyRepository).delete(existing);
        verify(auditService).record(AuditEventType.PROPERTY_DELETED, ADMIN, "North Site", "2 locations removed");
    }

    @Test
    void deletingAPropertyWithoutLocationsHasNoNote() {
        Property existing = property(9, "North Site", null, null, 0);
        when(propertyRepository.findById(9L)).thenReturn(Optional.of(existing));

        service.deleteProperty(9L, ADMIN);

        verify(auditService).record(AuditEventType.PROPERTY_DELETED, ADMIN, "North Site", null);
    }

    @Test
    void reportsAMissingProperty() {
        when(propertyRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(PropertyNotFoundException.class, () -> service.deleteProperty(9L, ADMIN));
    }

    // ------------------------------------------------------------------ read

    @Test
    void listsPropertiesByNameWithTheirRegionEditorAndLocationCount() {
        when(propertyRepository.findAll()).thenReturn(List.of(
                property(2, "beta site", region(1, "North"), null, 1),
                property(1, "Alpha Site", region(2, "South"), user(5, RoleType.EDITOR, false), 3)));

        List<PropertyRow> rows = service.list();

        assertEquals(List.of("Alpha Site", "beta site"), rows.stream().map(PropertyRow::name).toList());
        assertEquals("South", rows.get(0).regionName());
        assertEquals("Ana Silva", rows.get(0).editorName());
        assertEquals(3, rows.get(0).locationCount());
        assertEquals("Not assigned", rows.get(1).editorName());
    }

    @Test
    void offersActiveEditorsAndTheCurrentOneEvenIfSuspended() {
        when(userRepository.findActiveByRoleType(RoleType.EDITOR)).thenReturn(List.of(user(5, RoleType.EDITOR, false)));
        when(userRepository.findById(6L)).thenReturn(Optional.of(user(6, RoleType.EDITOR, true)));

        List<Option> options = service.editorOptions(6L);

        assertEquals(2, options.size());
        assertTrue(options.get(1).label().contains("suspended"));
    }
}
