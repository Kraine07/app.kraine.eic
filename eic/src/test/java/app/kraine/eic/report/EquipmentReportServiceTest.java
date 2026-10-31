package app.kraine.eic.report;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import app.kraine.eic.equipmentManagement.EquipmentRow;
import app.kraine.eic.exception.InvalidReportSelectionException;
import app.kraine.eic.model.Property;
import app.kraine.eic.model.Region;
import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.reportManagement.ReportViews.Option;
import app.kraine.eic.reportManagement.ReportViews.AllEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.EditorEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.PropertyEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.RegionEquipmentReport;
import app.kraine.eic.repository.EquipmentRepoInterface;
import app.kraine.eic.repository.PropertyRepoInterface;
import app.kraine.eic.repository.RegionRepoInterface;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.service.EquipmentReportService;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentReportServiceTest {

    private static final String ADMIN = "admin@example.com";
    private static final String EDITOR = "ana@example.com";

    @Mock private EquipmentRepoInterface equipmentRepository;
    @Mock private UserRepoInterface userRepository;
    @Mock private PropertyRepoInterface propertyRepository;
    @Mock private RegionRepoInterface regionRepository;

    private EquipmentReportService service;

    @BeforeEach
    void setUp() {
        service = new EquipmentReportService(equipmentRepository, userRepository, propertyRepository, regionRepository);
    }

    private static User user(long id, String first, String last, String email, RoleType type, boolean suspended) {
        Role role = new Role();
        role.setRoleType(type);
        User user = new User();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(email);
        user.setRole(role);
        user.setIsSuspended(suspended);
        return user;
    }

    private User givenAdministrator() {
        User admin = user(1, "Root", "Admin", ADMIN, RoleType.ADMINISTRATOR, false);
        when(userRepository.findByEmailIgnoreCase(ADMIN)).thenReturn(Optional.of(admin));
        return admin;
    }

    private User givenEditor() {
        User editor = user(2, "Ana", "Silva", EDITOR, RoleType.EDITOR, false);
        when(userRepository.findByEmailIgnoreCase(EDITOR)).thenReturn(Optional.of(editor));
        return editor;
    }

    private static EquipmentRow row(long id, String serial) {
        return new EquipmentRow(id, serial, YearMonth.of(2020, 5), "Acme", "X100", "North Site", "Store");
    }

    // ------------------------------------------------------------------ choosing an editor

    @Test
    void anAdministratorCanChooseAnyEditorSortedByNameAndNeverAnAdministrator() {
        givenAdministrator();
        when(userRepository.findAllWithDetails()).thenReturn(List.of(
                user(1, "Root", "Admin", ADMIN, RoleType.ADMINISTRATOR, false),
                user(2, "Ana", "Silva", EDITOR, RoleType.EDITOR, false),
                user(3, "Eva", "Alves", "eva@example.com", RoleType.EDITOR, true)));

        List<Option> options = service.editorOptions(ADMIN);

        assertEquals(2, options.size());
        assertEquals(3L, options.get(0).id());
        assertTrue(options.get(0).label().contains("Eva Alves"));
        assertTrue(options.get(0).label().contains("suspended"));
        assertEquals(2L, options.get(1).id());
        assertFalse(options.get(1).label().contains("suspended"));
    }

    @Test
    void anEditorCanOnlyChooseThemselves() {
        givenEditor();

        List<Option> options = service.editorOptions(EDITOR);

        assertEquals(1, options.size());
        assertEquals(2L, options.get(0).id());
        verify(userRepository, never()).findAllWithDetails();
    }

    // ------------------------------------------------------------------ generating the report

    @Test
    void anAdministratorGeneratesTheReportForAnEditor() {
        givenAdministrator();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2, "Ana", "Silva", EDITOR, RoleType.EDITOR, false)));
        when(equipmentRepository.findRowsForEditor(2L)).thenReturn(List.of(row(5, "SN-1"), row(6, "SN-2")));

        EditorEquipmentReport report = service.generateEditorReport(2L, ADMIN);

        assertEquals("Ana Silva", report.editorName());
        assertEquals(EDITOR, report.editorEmail());
        assertEquals(2, report.rows().size());
        assertTrue(report.generatedAt().endsWith(" UTC"));
    }

    @Test
    void anEditorGeneratesTheReportAboutThemselves() {
        givenEditor();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2, "Ana", "Silva", EDITOR, RoleType.EDITOR, false)));
        when(equipmentRepository.findRowsForEditor(2L)).thenReturn(List.of(row(5, "SN-1")));

        assertEquals(1, service.generateEditorReport(2L, EDITOR).rows().size());
    }

    @Test
    void anEditorCannotGenerateTheReportForSomeoneElse() {
        givenEditor();
        when(userRepository.findById(3L)).thenReturn(
                Optional.of(user(3, "Eva", "Alves", "eva@example.com", RoleType.EDITOR, false)));

        assertThrows(AccessDeniedException.class, () -> service.generateEditorReport(3L, EDITOR));
        verify(equipmentRepository, never()).findRowsForEditor(any());
    }

    @Test
    void anEditorWhoHasNoEquipmentGivesAnEmptyReport() {
        givenAdministrator();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2, "Ana", "Silva", EDITOR, RoleType.EDITOR, false)));
        when(equipmentRepository.findRowsForEditor(2L)).thenReturn(List.of());

        assertTrue(service.generateEditorReport(2L, ADMIN).rows().isEmpty());
    }

    @Test
    void rejectsAnEditorThatNoLongerExists() {
        givenAdministrator();
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        InvalidReportSelectionException ex = assertThrows(InvalidReportSelectionException.class,
                () -> service.generateEditorReport(9L, ADMIN));

        assertTrue(ex.getMessage().contains("no longer exists"));
    }

    @Test
    void rejectsAUserWhoIsNotAnEditor() {
        givenAdministrator();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, "Root", "Admin", ADMIN, RoleType.ADMINISTRATOR, false)));

        InvalidReportSelectionException ex = assertThrows(InvalidReportSelectionException.class,
                () -> service.generateEditorReport(1L, ADMIN));

        assertTrue(ex.getMessage().contains("not an editor"));
        verify(equipmentRepository, never()).findRowsForEditor(any());
    }

    // ------------------------------------------------------------------ UC-39: property report

    private static Property property(long id, String name, String regionName, User editor) {
        Property property = new Property();
        property.setId(id);
        property.setName(name);
        if (regionName != null) {
            Region region = new Region();
            region.setName(regionName);
            property.setRegion(region);
        }
        property.setUser(editor);
        return property;
    }

    @Test
    void anAdministratorCanChooseAnyPropertySortedByName() {
        givenAdministrator();
        when(propertyRepository.findAll()).thenReturn(List.of(
                property(2, "south site", null, null), property(1, "North Site", null, null)));

        List<Option> options = service.propertyOptions(ADMIN);

        assertEquals(List.of("North Site", "south site"), options.stream().map(Option::label).toList());
        verify(propertyRepository, never()).findByUserId(any());
    }

    @Test
    void anEditorCanOnlyChooseTheirOwnProperties() {
        givenEditor();
        when(propertyRepository.findByUserId(2L)).thenReturn(List.of(property(1, "North Site", null, null)));

        assertEquals(1, service.propertyOptions(EDITOR).size());
        verify(propertyRepository, never()).findAll();
    }

    @Test
    void anAdministratorGeneratesThePropertyReportWithItsRegionAndEditor() {
        givenAdministrator();
        User ana = user(2, "Ana", "Silva", EDITOR, RoleType.EDITOR, false);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property(10, "North Site", "North", ana)));
        when(equipmentRepository.findRowsForProperty(10L)).thenReturn(List.of(row(5, "SN-1")));

        PropertyEquipmentReport report = service.generatePropertyReport(10L, ADMIN);

        assertEquals("North Site", report.propertyName());
        assertEquals("North", report.regionName());
        assertEquals("Ana Silva", report.editorName());
        assertEquals(1, report.rows().size());
        assertTrue(report.generatedAt().endsWith(" UTC"));
    }

    @Test
    void showsNotSetAndNotAssignedWhenThePropertyHasNoRegionOrEditor() {
        givenAdministrator();
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property(10, "North Site", null, null)));
        when(equipmentRepository.findRowsForProperty(10L)).thenReturn(List.of());

        PropertyEquipmentReport report = service.generatePropertyReport(10L, ADMIN);

        assertEquals("Not set", report.regionName());
        assertEquals("Not assigned", report.editorName());
        assertTrue(report.rows().isEmpty());
    }

    @Test
    void anEditorGeneratesTheReportForTheirOwnProperty() {
        User ana = givenEditor();
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property(10, "North Site", "North", ana)));
        when(equipmentRepository.findRowsForProperty(10L)).thenReturn(List.of(row(5, "SN-1")));

        assertEquals(1, service.generatePropertyReport(10L, EDITOR).rows().size());
    }

    @Test
    void anEditorCannotGenerateTheReportForAnotherEditorsProperty() {
        givenEditor();
        User other = user(3, "Eva", "Alves", "eva@example.com", RoleType.EDITOR, false);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property(10, "North Site", "North", other)));

        assertThrows(AccessDeniedException.class, () -> service.generatePropertyReport(10L, EDITOR));
        verify(equipmentRepository, never()).findRowsForProperty(any());
    }

    @Test
    void anEditorCannotGenerateTheReportForAPropertyWithoutAnEditor() {
        givenEditor();
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property(10, "North Site", "North", null)));

        assertThrows(AccessDeniedException.class, () -> service.generatePropertyReport(10L, EDITOR));
    }

    @Test
    void rejectsAPropertyThatNoLongerExists() {
        givenAdministrator();
        when(propertyRepository.findById(9L)).thenReturn(Optional.empty());

        InvalidReportSelectionException ex = assertThrows(InvalidReportSelectionException.class,
                () -> service.generatePropertyReport(9L, ADMIN));

        assertTrue(ex.getMessage().contains("no longer exists"));
    }

    // ------------------------------------------------------------------ UC-40: region report

    private static Region region(long id, String name) {
        Region region = new Region();
        region.setId(id);
        region.setName(name);
        return region;
    }

    @Test
    void anAdministratorCanChooseAnyRegionSortedByName() {
        givenAdministrator();
        when(regionRepository.findAll()).thenReturn(List.of(region(2, "south"), region(1, "North")));

        List<Option> options = service.regionOptions(ADMIN);

        assertEquals(List.of("North", "south"), options.stream().map(Option::label).toList());
        verify(propertyRepository, never()).findByUserId(any());
    }

    @Test
    void anEditorCanChooseTheRegionsTheirPropertiesAreIn() {
        User ana = givenEditor();
        Region north = region(5, "North");
        Property a = property(1, "A", null, ana);
        a.setRegion(north);
        Property b = property(2, "B", null, ana);
        b.setRegion(north); // the same region twice must be offered once
        Property c = property(3, "C", null, ana);
        c.setRegion(region(6, "East"));
        Property d = property(4, "D", null, ana); // no region at all
        when(propertyRepository.findByUserId(2L)).thenReturn(List.of(a, b, c, d));

        List<Option> options = service.regionOptions(EDITOR);

        assertEquals(List.of("East", "North"), options.stream().map(Option::label).toList());
        verify(regionRepository, never()).findAll();
    }

    @Test
    void anAdministratorGeneratesTheRegionReportWithEveryItem() {
        givenAdministrator();
        when(regionRepository.findById(5L)).thenReturn(Optional.of(region(5, "North")));
        when(equipmentRepository.findRowsForRegion(5L)).thenReturn(List.of(row(5, "SN-1"), row(6, "SN-2")));

        RegionEquipmentReport report = service.generateRegionReport(5L, ADMIN);

        assertEquals("North", report.regionName());
        assertEquals(2, report.rows().size());
        assertFalse(report.restricted());
        assertTrue(report.generatedAt().endsWith(" UTC"));
        verify(propertyRepository, never()).existsByRegionIdAndUserId(any(), any());
    }

    @Test
    void anEditorGetsOnlyTheirOwnPartOfTheRegion() {
        givenEditor();
        when(regionRepository.findById(5L)).thenReturn(Optional.of(region(5, "North")));
        when(propertyRepository.existsByRegionIdAndUserId(5L, 2L)).thenReturn(true);
        when(equipmentRepository.findRowsForRegionAndEditor(5L, 2L)).thenReturn(List.of(row(5, "SN-1")));

        RegionEquipmentReport report = service.generateRegionReport(5L, EDITOR);

        assertEquals(1, report.rows().size());
        assertTrue(report.restricted());
        verify(equipmentRepository, never()).findRowsForRegion(any());
    }

    @Test
    void anEditorCannotGenerateTheReportForARegionWithNoneOfTheirProperties() {
        givenEditor();
        when(regionRepository.findById(5L)).thenReturn(Optional.of(region(5, "North")));
        when(propertyRepository.existsByRegionIdAndUserId(5L, 2L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.generateRegionReport(5L, EDITOR));
        verify(equipmentRepository, never()).findRowsForRegionAndEditor(any(), any());
    }

    @Test
    void aRegionWithNoEquipmentGivesAnEmptyReport() {
        givenAdministrator();
        when(regionRepository.findById(5L)).thenReturn(Optional.of(region(5, "North")));
        when(equipmentRepository.findRowsForRegion(5L)).thenReturn(List.of());

        assertTrue(service.generateRegionReport(5L, ADMIN).rows().isEmpty());
    }

    @Test
    void rejectsARegionThatNoLongerExists() {
        givenAdministrator();
        when(regionRepository.findById(9L)).thenReturn(Optional.empty());

        InvalidReportSelectionException ex = assertThrows(InvalidReportSelectionException.class,
                () -> service.generateRegionReport(9L, ADMIN));

        assertTrue(ex.getMessage().contains("no longer exists"));
    }

    // ------------------------------------------------------------------ UC-41: all equipment

    @Test
    void anAdministratorGetsAllEquipment() {
        givenAdministrator();
        when(equipmentRepository.findAllRows()).thenReturn(List.of(row(5, "SN-1"), row(6, "SN-2"), row(7, "SN-3")));

        AllEquipmentReport report = service.generateAllEquipmentReport(ADMIN);

        assertEquals(3, report.rows().size());
        assertFalse(report.restricted());
        assertTrue(report.generatedAt().endsWith(" UTC"));
        verify(equipmentRepository, never()).findRowsForEditor(any());
    }

    @Test
    void anEditorOnlyGetsTheEquipmentAtTheirOwnProperties() {
        givenEditor();
        when(equipmentRepository.findRowsForEditor(2L)).thenReturn(List.of(row(5, "SN-1")));

        AllEquipmentReport report = service.generateAllEquipmentReport(EDITOR);

        assertEquals(1, report.rows().size());
        assertTrue(report.restricted());
        verify(equipmentRepository, never()).findAllRows();
    }

    @Test
    void whenThereIsNoEquipmentTheReportIsEmpty() {
        givenAdministrator();
        when(equipmentRepository.findAllRows()).thenReturn(List.of());

        assertTrue(service.generateAllEquipmentReport(ADMIN).rows().isEmpty());
    }
}
