package app.kraine.eic.usermanagement;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.auth.TemporaryPasswordGenerator;
import app.kraine.eic.auth.TemporaryPasswordMailer;
import app.kraine.eic.exception.DuplicateEmailException;
import app.kraine.eic.exception.EmailDeliveryException;
import app.kraine.eic.exception.LastAdministratorException;
import app.kraine.eic.exception.UserLinkedToPropertyException;
import app.kraine.eic.exception.UserNotFoundException;
import app.kraine.eic.form.UserForm;
import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.RoleRepositoryInterface;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.service.UserService;
import app.kraine.eic.userManagement.LastAdministratorGuard;
import app.kraine.eic.userManagement.UserPropertyLinks;
import app.kraine.eic.userManagement.UserViews;
import app.kraine.eic.userManagement.UserViews.UserDashboard;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock private UserRepoInterface userRepository;
    @Mock private RoleRepositoryInterface roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TemporaryPasswordGenerator passwordGenerator;
    @Mock private TemporaryPasswordMailer mailer;
    @Mock private AuditService auditService;
    @Mock private ObjectProvider<UserPropertyLinks> propertyLinks;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, roleRepository, passwordEncoder, passwordGenerator,
                mailer, new LastAdministratorGuard(userRepository), propertyLinks,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), auditService);
    }

    private static Role role(RoleType type) {
        Role role = new Role();
        role.setRoleType(type);
        return role;
    }

    private static User user(long id, RoleType type, boolean suspended, String first, String last) {
        User user = new User();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(first.toLowerCase() + "@example.com");
        user.setRole(role(type));
        user.setIsSuspended(suspended);
        user.setFailedAttempts(0);
        user.setIsTemporaryPassword(false);
        return user;
    }

    private static User activeUser(long id, RoleType type) {
        return user(id, type, false, "Ana", "Silva");
    }

    private static User suspendedUser(long id, RoleType type) {
        return user(id, type, true, "Ana", "Silva");
    }

    private static UserForm form() {
        UserForm form = new UserForm();
        form.setFirstName(" Ana ");
        form.setLastName("Silva");
        form.setEmail(" ana@example.com ");
        form.setRoleType(RoleType.EDITOR);
        return form;
    }

    // ------------------------------------------------------------------ create

    @Test
    void createsAnActiveUserWithATemporaryPasswordAndEmailsIt() {
        Role editor = role(RoleType.EDITOR);
        when(userRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(false);
        when(roleRepository.findByRoleType(RoleType.EDITOR)).thenReturn(editor);
        when(passwordGenerator.generate()).thenReturn("Temp#Pass12");
        when(passwordEncoder.encode("Temp#Pass12")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createUser(form(), "admin@example.com");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertEquals("Ana", user.getFirstName());
        assertEquals("ana@example.com", user.getEmail());
        assertEquals("hashed", user.getPassword());
        assertSame(editor, user.getRole());
        assertFalse(user.getIsSuspended());
        assertTrue(user.getIsTemporaryPassword());
        verify(auditService).record(AuditEventType.USER_CREATED, "admin@example.com", "ana@example.com", "Role EDITOR");
        verify(mailer).sendTemporaryPassword("ana@example.com", "Ana", "Temp#Pass12");
    }

    @Test
    void rejectsADuplicateEmailWithoutSavingOrEmailing() {
        when(userRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> service.createUser(form(), "admin@example.com"));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(mailer);
    }

    @Test
    void failsWhenTheEmailCannotBeSentSoTheTransactionRollsBack() {
        when(userRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(false);
        when(roleRepository.findByRoleType(RoleType.EDITOR)).thenReturn(role(RoleType.EDITOR));
        when(passwordGenerator.generate()).thenReturn("Temp#Pass12");
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new EmailDeliveryException("The email could not be sent."))
                .when(mailer).sendTemporaryPassword(any(), any(), any());

        assertThrows(EmailDeliveryException.class, () -> service.createUser(form(), "admin@example.com"));
    }

    // ------------------------------------------------------------------ update

    @Test
    void updatesNamesAndEmail() {
        User user = user(5, RoleType.EDITOR, false, "Old", "Name");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("ana@example.com", 5L)).thenReturn(false);

        service.updateUser(5L, form(), "admin@example.com");

        assertEquals("Ana", user.getFirstName());
        assertEquals("Silva", user.getLastName());
        assertEquals("ana@example.com", user.getEmail());
        verify(userRepository).saveAndFlush(user);
        verify(auditService).record(AuditEventType.USER_UPDATED, "admin@example.com", "ana@example.com", null);
    }

    @Test
    void rejectsAnEmailUsedByAnotherUser() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(activeUser(5, RoleType.EDITOR)));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("ana@example.com", 5L)).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> service.updateUser(5L, form(), "admin@example.com"));
        verify(userRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ suspend

    @Test
    void refusesToSuspendTheLastActiveAdministrator() {
        User admin = activeUser(1, RoleType.ADMINISTRATOR);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleTypeAndStatus(RoleType.ADMINISTRATOR, false)).thenReturn(1L);

        assertThrows(LastAdministratorException.class, () -> service.suspend(1L, "admin@example.com"));
        assertFalse(admin.getIsSuspended());
        verify(userRepository, never()).save(any());
    }

    @Test
    void suspendsAnAdministratorWhenAnotherOneIsActive() {
        User admin = activeUser(1, RoleType.ADMINISTRATOR);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleTypeAndStatus(RoleType.ADMINISTRATOR, false)).thenReturn(2L);

        service.suspend(1L, "admin@example.com");

        assertTrue(admin.getIsSuspended());
        verify(userRepository).save(admin);
        verify(auditService).record(AuditEventType.USER_SUSPENDED, "admin@example.com", "ana@example.com", null);
    }

    @Test
    void suspendsAnEditorWithoutCountingAdministrators() {
        User editor = activeUser(2, RoleType.EDITOR);
        when(userRepository.findById(2L)).thenReturn(Optional.of(editor));

        service.suspend(2L, "admin@example.com");

        assertTrue(editor.getIsSuspended());
        verify(userRepository, never()).countByRoleTypeAndStatus(any(),any());
    }

    // ------------------------------------------------------------------ activate

    @Test
    void activatesASuspendedUserWithANewTemporaryPassword() {
        User user = suspendedUser(3, RoleType.EDITOR);
        user.setFailedAttempts(5);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(passwordGenerator.generate()).thenReturn("Temp#Pass12");
        when(passwordEncoder.encode("Temp#Pass12")).thenReturn("hashed");

        service.activate(3L, "admin@example.com");

        assertFalse(user.getIsSuspended());
        assertEquals(0, user.getFailedAttempts());
        assertEquals("hashed", user.getPassword());
        assertTrue(user.getIsTemporaryPassword());
        verify(auditService).record(AuditEventType.USER_ACTIVATED, "admin@example.com", "ana@example.com", null);
        verify(mailer).sendTemporaryPassword("ana@example.com", "Ana", "Temp#Pass12");
    }

    // ------------------------------------------------------------------ delete

    @Test
    void refusesToDeleteAUserLinkedToAProperty() {
        User user = activeUser(4, RoleType.EDITOR);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(propertyLinks.getIfAvailable()).thenReturn(userId -> true);

        assertThrows(UserLinkedToPropertyException.class, () -> service.delete(4L, "admin@example.com"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void refusesToDeleteTheLastActiveAdministrator() {
        User admin = activeUser(1, RoleType.ADMINISTRATOR);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleTypeAndStatus(RoleType.ADMINISTRATOR, false)).thenReturn(1L);

        assertThrows(LastAdministratorException.class, () -> service.delete(1L, "admin@example.com"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deletesAnUnlinkedEditor() {
        User editor = activeUser(2, RoleType.EDITOR);
        when(userRepository.findById(2L)).thenReturn(Optional.of(editor));

        service.delete(2L, "admin@example.com");

        verify(userRepository).delete(editor);
        verify(auditService).record(AuditEventType.USER_DELETED, "admin@example.com", "ana@example.com", null);
    }

    @Test
    void reportsAMissingUser() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.delete(9L, "admin@example.com"));
    }

    // ------------------------------------------------------------------ dashboard

    @Test
    void buildsTheDashboardSortedByNameWithCounts() {
        when(userRepository.findAllWithDetails()).thenReturn(List.of(
                user(1, RoleType.ADMINISTRATOR, false, "Rui", "Lima"),
                user(2, RoleType.EDITOR, true, "Eva", "Alves"),
                user(3, RoleType.EDITOR, false, "Tia", "Costa")));

        UserDashboard dashboard = service.dashboard();

        assertEquals(List.of("Eva Alves", "Tia Costa", "Rui Lima"),
                dashboard.users().stream().map(UserViews.UserRow::fullName).toList());
        assertEquals(3, dashboard.totalUsers());
        assertEquals(1, dashboard.activeAdministrators());
        assertEquals(1, dashboard.suspendedUsers());
        assertEquals("Administrator", dashboard.users().get(2).roleLabel());
        assertFalse(dashboard.users().get(0).active());
    }

    @Test
    void labelsEnumsForDisplay() {
        assertEquals("Administrator", UserService.label(RoleType.ADMINISTRATOR));
        assertEquals("Editor", UserService.label(RoleType.EDITOR));
    }
}
