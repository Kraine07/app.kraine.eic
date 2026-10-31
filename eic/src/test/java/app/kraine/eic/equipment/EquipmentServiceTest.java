package app.kraine.eic.equipment;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.equipmentManagement.CompositeKeys;
import app.kraine.eic.equipmentManagement.EquipmentViews;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentDetail;
import app.kraine.eic.equipmentManagement.EquipmentViews.EquipmentEditData;
import app.kraine.eic.exception.DuplicateSerialNumberException;
import app.kraine.eic.exception.EquipmentInUseException;
import app.kraine.eic.exception.EquipmentNotFoundException;
import app.kraine.eic.exception.InvalidEquipmentReferenceException;
import app.kraine.eic.form.EquipmentForm;
import app.kraine.eic.model.Equipment;
import app.kraine.eic.model.EquipmentImage;
import app.kraine.eic.model.Location;
import app.kraine.eic.model.LocationId;
import app.kraine.eic.model.Manufacturer;
import app.kraine.eic.model.Model;
import app.kraine.eic.model.ModelId;
import app.kraine.eic.model.Property;
import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.equipmentManagement.EquipmentViews.Option;
import app.kraine.eic.repository.EquipmentImageRepository;
import app.kraine.eic.repository.EquipmentRepoInterface;
import app.kraine.eic.repository.LocationRepoInterface;
import app.kraine.eic.repository.ModelRepoInterface;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.service.EquipmentImageService;
import app.kraine.eic.service.EquipmentService;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    private static final String ADMIN = "admin@example.com";
    private static final String EDITOR = "editor@example.com";

    @Mock private EquipmentRepoInterface equipmentRepository;
    @Mock private ModelRepoInterface modelRepository;
    @Mock private LocationRepoInterface locationRepository;
    @Mock private UserRepoInterface userRepository;
    @Mock private EquipmentImageService imageService;
    @Mock private AuditService auditService;

    private EquipmentService service;

    @BeforeEach
    void setUp() {
        service = new EquipmentService(equipmentRepository, modelRepository, locationRepository, userRepository,
                imageService, auditService);
    }

    // ------------------------------------------------------------------ data helpers

    private static User user(long id, String email, RoleType type) {
        Role role = new Role();
        role.setRoleType(type);
        User user = new User();
        user.setId(id);
        user.setFirstName("Ana");
        user.setLastName("Silva");
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

    private static Manufacturer manufacturer(long id, String name) {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(id);
        manufacturer.setName(name);
        return manufacturer;
    }

    private static Equipment equipment(long id, String serial, Model model, Location location) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setSerialNumber(serial);
        equipment.setModel(model);
        equipment.setLocation(location);
        return equipment;
    }

    private static String modelKey(Model model) {
        return CompositeKeys.encode(model.getManufacturer().getId(), model.getDescription());
    }

    private static String locationKey(Location location) {
        return CompositeKeys.encode(location.getProperty().getId(), location.getName());
    }

    private static EquipmentForm form(String serial, String month, Model model, Location location) {
        EquipmentForm form = new EquipmentForm();
        form.setSerialNumber(serial);
        form.setManufacturedDate(month);
        form.setModelKey(modelKey(model));
        form.setLocationKey(locationKey(location));
        return form;
    }

    private void givenModelAndLocationExist(Model model, Location location) {
        when(modelRepository.findById(any(ModelId.class))).thenReturn(Optional.of(model));
        when(locationRepository.findById(any(LocationId.class))).thenReturn(Optional.of(location));
    }

    private final Manufacturer acme = manufacturer(1, "Acme");
    private final Model x100 = new Model(acme, "X100");

    // ------------------------------------------------------------------ create

    @Test
    void anAdministratorCreatesEquipmentAndAuditsIt() {
        givenAdministrator();
        Location store = new Location(property(10, "North Site", null), "Store");
        givenModelAndLocationExist(x100, store);
        when(equipmentRepository.existsBySerialNumberIgnoreCase("SN-1")).thenReturn(false);
        when(equipmentRepository.saveAndFlush(any(Equipment.class))).thenAnswer(i -> i.getArgument(0));

        service.createEquipment(form(" SN-1 ", "2020-05", x100, store), ADMIN);

        ArgumentCaptor<Equipment> saved = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).saveAndFlush(saved.capture());
        assertEquals("SN-1", saved.getValue().getSerialNumber());
        assertEquals(YearMonth.of(2020, 5), saved.getValue().getManufacturedDate());
        assertSame(x100, saved.getValue().getModel());
        assertSame(store, saved.getValue().getLocation());
        verify(auditService).record(AuditEventType.EQUIPMENT_CREATED, ADMIN, "SN-1",
                "Acme / X100 at North Site / Store");
    }

    @Test
    void aBlankSerialNumberIsStoredAsNoneAndNeverClashes() {
        givenAdministrator();
        Location store = new Location(property(10, "North Site", null), "Store");
        givenModelAndLocationExist(x100, store);
        when(equipmentRepository.saveAndFlush(any(Equipment.class))).thenAnswer(i -> {
            Equipment e = i.getArgument(0);
            e.setId(7L);
            return e;
        });

        service.createEquipment(form("   ", "", x100, store), ADMIN);

        ArgumentCaptor<Equipment> saved = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).saveAndFlush(saved.capture());
        assertNull(saved.getValue().getSerialNumber());
        assertNull(saved.getValue().getManufacturedDate());
        verify(equipmentRepository, never()).existsBySerialNumberIgnoreCase(any());
        verify(auditService).record(AuditEventType.EQUIPMENT_CREATED, ADMIN, "Equipment #7",
                "Acme / X100 at North Site / Store");
    }

    @Test
    void rejectsADuplicateSerialNumber() {
        givenAdministrator();
        Location store = new Location(property(10, "North Site", null), "Store");
        givenModelAndLocationExist(x100, store);
        when(equipmentRepository.existsBySerialNumberIgnoreCase("SN-1")).thenReturn(true);

        assertThrows(DuplicateSerialNumberException.class,
                () -> service.createEquipment(form("SN-1", "", x100, store), ADMIN));
        verify(equipmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void treatsAUniqueConstraintClashAsADuplicate() {
        givenAdministrator();
        Location store = new Location(property(10, "North Site", null), "Store");
        givenModelAndLocationExist(x100, store);
        when(equipmentRepository.existsBySerialNumberIgnoreCase("SN-1")).thenReturn(false);
        when(equipmentRepository.saveAndFlush(any(Equipment.class))).thenThrow(new DataIntegrityViolationException("unique"));

        assertThrows(DuplicateSerialNumberException.class,
                () -> service.createEquipment(form("SN-1", "", x100, store), ADMIN));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void rejectsAModelKeyThatIsMissingOrUnknown() {
        givenAdministrator();
        Location store = new Location(property(10, "North Site", null), "Store");
        EquipmentForm missing = form("SN-1", "", x100, store);
        missing.setModelKey("not a key");

        InvalidEquipmentReferenceException none = assertThrows(InvalidEquipmentReferenceException.class,
                () -> service.createEquipment(missing, ADMIN));
        assertEquals("modelKey", none.getField());
        assertEquals("Select a model.", none.getMessage());

        when(modelRepository.findById(any(ModelId.class))).thenReturn(Optional.empty());
        InvalidEquipmentReferenceException gone = assertThrows(InvalidEquipmentReferenceException.class,
                () -> service.createEquipment(form("SN-1", "", x100, store), ADMIN));
        assertEquals("The selected model no longer exists.", gone.getMessage());
    }

    @Test
    void rejectsALocationThatDoesNotExist() {
        givenAdministrator();
        when(modelRepository.findById(any(ModelId.class))).thenReturn(Optional.of(x100));
        when(locationRepository.findById(any(LocationId.class))).thenReturn(Optional.empty());
        Location store = new Location(property(10, "North Site", null), "Store");

        InvalidEquipmentReferenceException ex = assertThrows(InvalidEquipmentReferenceException.class,
                () -> service.createEquipment(form("SN-1", "", x100, store), ADMIN));

        assertEquals("locationKey", ex.getField());
    }

    @Test
    void anEditorCreatesEquipmentAtTheirOwnProperty() {
        User editor = givenEditor();
        Location store = new Location(property(10, "North Site", editor), "Store");
        givenModelAndLocationExist(x100, store);
        when(equipmentRepository.existsBySerialNumberIgnoreCase("SN-1")).thenReturn(false);
        when(equipmentRepository.saveAndFlush(any(Equipment.class))).thenAnswer(i -> i.getArgument(0));

        service.createEquipment(form("SN-1", "", x100, store), EDITOR);

        verify(equipmentRepository).saveAndFlush(any(Equipment.class));
    }

    @Test
    void anEditorCannotCreateEquipmentAtSomeoneElsesProperty() {
        givenEditor();
        Location store = new Location(property(10, "North Site", user(3, "other@example.com", RoleType.EDITOR)), "Store");
        givenModelAndLocationExist(x100, store);

        assertThrows(AccessDeniedException.class, () -> service.createEquipment(form("SN-1", "", x100, store), EDITOR));
        verify(equipmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnInvalidOrFutureManufacturedMonth() {
        givenAdministrator();
        Location store = new Location(property(10, "North Site", null), "Store");
        givenModelAndLocationExist(x100, store);

        InvalidEquipmentReferenceException invalid = assertThrows(InvalidEquipmentReferenceException.class,
                () -> service.createEquipment(form("", "May 2020", x100, store), ADMIN));
        assertEquals("manufacturedDate", invalid.getField());

        String nextMonth = YearMonth.now().plusMonths(1).toString();
        InvalidEquipmentReferenceException future = assertThrows(InvalidEquipmentReferenceException.class,
                () -> service.createEquipment(form("", nextMonth, x100, store), ADMIN));
        assertTrue(future.getMessage().contains("future"));
    }

    @Test
    void parsesMonthsTheWayAnHtmlMonthInputSendsThem() {
        assertNull(EquipmentService.parseMonth(null));
        assertNull(EquipmentService.parseMonth("  "));
        assertEquals(YearMonth.of(2020, 5), EquipmentService.parseMonth(" 2020-05 "));
        assertEquals(YearMonth.now(), EquipmentService.parseMonth(YearMonth.now().toString()));
    }

    // ------------------------------------------------------------------ update

    @Test
    void updatesEquipmentAndAuditsIt() {
        givenAdministrator();
        Location store = new Location(property(10, "North Site", null), "Store");
        Location yard = new Location(property(11, "South Site", null), "Yard");
        Equipment existing = equipment(5, "OLD", x100, store);
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        Model y200 = new Model(acme, "Y200");
        givenModelAndLocationExist(y200, yard);
        when(equipmentRepository.existsBySerialNumberIgnoreCaseAndIdNot("NEW", 5L)).thenReturn(false);

        service.updateEquipment(5L, form("NEW", "2021-03", y200, yard), ADMIN);

        assertEquals("NEW", existing.getSerialNumber());
        assertEquals(YearMonth.of(2021, 3), existing.getManufacturedDate());
        assertSame(y200, existing.getModel());
        assertSame(yard, existing.getLocation());
        verify(equipmentRepository).saveAndFlush(existing);
        verify(auditService).record(AuditEventType.EQUIPMENT_UPDATED, ADMIN, "NEW", "Acme / Y200 at South Site / Yard");
    }

    @Test
    void rejectsASerialNumberUsedByAnotherItem() {
        givenAdministrator();
        Location store = new Location(property(10, "North Site", null), "Store");
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(equipment(5, "OLD", x100, store)));
        givenModelAndLocationExist(x100, store);
        when(equipmentRepository.existsBySerialNumberIgnoreCaseAndIdNot("TAKEN", 5L)).thenReturn(true);

        assertThrows(DuplicateSerialNumberException.class,
                () -> service.updateEquipment(5L, form("TAKEN", "", x100, store), ADMIN));
        verify(equipmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void anEditorCannotUpdateEquipmentAtAnotherEditorsProperty() {
        givenEditor();
        Location store = new Location(property(10, "North Site", user(3, "other@example.com", RoleType.EDITOR)), "Store");
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(equipment(5, "SN-1", x100, store)));

        assertThrows(AccessDeniedException.class,
                () -> service.updateEquipment(5L, form("SN-1", "", x100, store), EDITOR));
    }

    @Test
    void anEditorCannotMoveEquipmentToSomeoneElsesLocation() {
        User editor = givenEditor();
        Location mine = new Location(property(10, "North Site", editor), "Store");
        Location theirs = new Location(property(11, "South Site", user(3, "other@example.com", RoleType.EDITOR)), "Yard");
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(equipment(5, "SN-1", x100, mine)));
        givenModelAndLocationExist(x100, theirs);

        assertThrows(AccessDeniedException.class,
                () -> service.updateEquipment(5L, form("SN-1", "", x100, theirs), EDITOR));
        verify(equipmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportsMissingEquipment() {
        givenAdministrator();
        when(equipmentRepository.findById(9L)).thenReturn(Optional.empty());
        Location store = new Location(property(10, "North Site", null), "Store");

        assertThrows(EquipmentNotFoundException.class,
                () -> service.updateEquipment(9L, form("SN-1", "", x100, store), ADMIN));
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deletesEquipmentAndAuditsIt() {
        givenAdministrator();
        Equipment existing = equipment(5, "SN-1", x100, new Location(property(10, "North Site", null), "Store"));
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(existing));

        service.deleteEquipment(5L, ADMIN);

        verify(equipmentRepository).delete(existing);
        verify(equipmentRepository).flush();
        verify(auditService).record(AuditEventType.EQUIPMENT_DELETED, ADMIN, "SN-1", null);
    }

    @Test
    void anEditorCannotDeleteEquipmentAtAnotherEditorsProperty() {
        givenEditor();
        Location store = new Location(property(10, "North Site", user(3, "other@example.com", RoleType.EDITOR)), "Store");
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(equipment(5, "SN-1", x100, store)));

        assertThrows(AccessDeniedException.class, () -> service.deleteEquipment(5L, EDITOR));
        verify(equipmentRepository, never()).delete(any());
    }

    @Test
    void explainsWhyEquipmentThatOtherRecordsUseCannotBeDeleted() {
        givenAdministrator();
        Equipment existing = equipment(5, "SN-1", x100, new Location(property(10, "North Site", null), "Store"));
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk")).when(equipmentRepository).flush();

        assertThrows(EquipmentInUseException.class, () -> service.deleteEquipment(5L, ADMIN));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ read

    @Test
    void anAdministratorSearchesAllEquipmentWithATidiedQuery() {
        givenAdministrator();
        when(equipmentRepository.search(eq("acme"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search("  ACME ", 2, ADMIN);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(equipmentRepository).search(eq("acme"), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(EquipmentService.PAGE_SIZE, pageable.getValue().getPageSize());
    }

    @Test
    void anEditorOnlySearchesTheirOwnPropertiesAndANegativePageIsTheFirst() {
        givenEditor();
        when(equipmentRepository.searchForEditor(eq(""), eq(2L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(null, -4, EDITOR);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(equipmentRepository).searchForEditor(eq(""), eq(2L), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        verify(equipmentRepository, never()).search(any(), any());
    }

    @Test
    void showsTheDetailsWithTheEditorAndTheImageName() {
        givenAdministrator();
        User editor = user(2, EDITOR, RoleType.EDITOR);
        Equipment existing = equipment(5, "SN-1", x100, new Location(property(10, "North Site", editor), "Store"));
        existing.setManufacturedDate(YearMonth.of(2020, 5));
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        EquipmentImageRepository.ImageInfo info = mock(EquipmentImageRepository.ImageInfo.class);
        when(info.getImageName()).thenReturn("front.png");
        when(imageService.findImageInfo(5L)).thenReturn(Optional.of(info));

        EquipmentDetail detail = service.findDetail(5L, ADMIN);

        assertEquals("SN-1", detail.serialNumber());
        assertEquals("Acme", detail.manufacturerName());
        assertEquals("X100", detail.modelDescription());
        assertEquals("North Site", detail.propertyName());
        assertEquals("Ana Silva", detail.editorName());
        assertEquals("front.png", detail.imageName());
    }

    @Test
    void showsNoImageNameAndNoEditorWhenThereAreNone() {
        givenAdministrator();
        Equipment existing = equipment(5, null, x100, new Location(property(10, "North Site", null), "Store"));
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(imageService.findImageInfo(5L)).thenReturn(Optional.empty());

        EquipmentDetail detail = service.findDetail(5L, ADMIN);

        assertNull(detail.imageName());
        assertEquals("Not assigned", detail.editorName());
    }

    @Test
    void anEditorCannotViewEquipmentAtAnotherEditorsProperty() {
        givenEditor();
        Location store = new Location(property(10, "North Site", user(3, "other@example.com", RoleType.EDITOR)), "Store");
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(equipment(5, "SN-1", x100, store)));

        assertThrows(AccessDeniedException.class, () -> service.findDetail(5L, EDITOR));
    }

    @Test
    void offersModelsAndLocationsAsEncodedKeysSortedByLabel() {
        givenAdministrator();
        Manufacturer globex = manufacturer(2, "globex");
        when(modelRepository.findAll()).thenReturn(List.of(new Model(globex, "Z1"), new Model(acme, "b"), new Model(acme, "A")));
        Property north = property(1, "North Site", null);
        when(locationRepository.findAll()).thenReturn(List.of(
                new Location(property(2, "South Site", null), "Yard"), new Location(north, "Store")));

        List<Option> models = service.modelOptions();
        List<Option> locations = service.locationOptions(ADMIN);

        assertEquals(List.of("Acme / A", "Acme / b", "globex / Z1"), models.stream().map(Option::label).toList());
        assertEquals(List.of("North Site / Store", "South Site / Yard"), locations.stream().map(Option::label).toList());
        assertEquals(new CompositeKeys.Key(1L, "A"), CompositeKeys.decode(models.get(0).key()));
    }

    @Test
    void anEditorIsOnlyOfferedTheirOwnLocations() {
        User editor = givenEditor();
        when(locationRepository.findByPropertyUserId(2L))
                .thenReturn(List.of(new Location(property(1, "North Site", editor), "Store")));

        List<Option> locations = service.locationOptions(EDITOR);

        assertEquals(List.of("North Site / Store"), locations.stream().map(Option::label).toList());
        verify(locationRepository, never()).findAll();
    }

    @Test
    void fillsTheEditFormWithKeysThatPointBackAtTheCurrentModelAndLocation() {
        givenAdministrator();
        Location store = new Location(property(10, "North Site", null), "Store");
        Equipment existing = equipment(5, "SN-1", x100, store);
        existing.setManufacturedDate(YearMonth.of(2020, 5));
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(existing));

        EquipmentEditData data = service.findForEdit(5L, ADMIN);

        assertEquals("SN-1", data.form().getSerialNumber());
        assertEquals("2020-05", data.form().getManufacturedDate());
        assertEquals(new CompositeKeys.Key(1L, "X100"), CompositeKeys.decode(data.form().getModelKey()));
        assertEquals(new CompositeKeys.Key(10L, "Store"), CompositeKeys.decode(data.form().getLocationKey()));
    }

    // ------------------------------------------------------------------ image content

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Test
    void servesTheImageWithATypeDetectedFromItsBytes() {
        givenAdministrator();
        Equipment existing = equipment(5, "SN-1", x100, new Location(property(10, "North Site", null), "Store"));
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        EquipmentImage image = new EquipmentImage();
        image.setImageData(PNG);
        when(imageService.findImage(5L)).thenReturn(Optional.of(image));

        EquipmentViews.ImageContent content = service.findImageContent(5L, ADMIN).orElseThrow();

        assertEquals("image/png", content.contentType());
        assertArrayEquals(PNG, content.data());
    }

    @Test
    void fallsBackToAGenericTypeWhenTheBytesAreNotARecognisedImage() {
        givenAdministrator();
        Equipment existing = equipment(5, "SN-1", x100, new Location(property(10, "North Site", null), "Store"));
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        EquipmentImage image = new EquipmentImage();
        image.setImageData("not an image".getBytes());
        when(imageService.findImage(5L)).thenReturn(Optional.of(image));

        assertEquals("application/octet-stream", service.findImageContent(5L, ADMIN).orElseThrow().contentType());
    }

    @Test
    void hasNoImageContentWhenTheEquipmentHasNoImage() {
        givenAdministrator();
        Equipment existing = equipment(5, "SN-1", x100, new Location(property(10, "North Site", null), "Store"));
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(imageService.findImage(5L)).thenReturn(Optional.empty());

        assertTrue(service.findImageContent(5L, ADMIN).isEmpty());
    }

    @Test
    void anEditorCannotSeeTheImageOfEquipmentAtAnotherEditorsProperty() {
        givenEditor();
        Location store = new Location(property(10, "North Site", user(3, "other@example.com", RoleType.EDITOR)), "Store");
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(equipment(5, "SN-1", x100, store)));

        assertThrows(AccessDeniedException.class, () -> service.findImageContent(5L, EDITOR));
        verify(imageService, never()).findImage(any());
    }
}
