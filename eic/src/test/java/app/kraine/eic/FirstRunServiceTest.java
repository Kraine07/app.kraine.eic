package app.kraine.eic;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import app.kraine.eic.exception.SetupAlreadyCompletedException;
import app.kraine.eic.form.SetupForm;
import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.RoleRepositoryInterface;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.service.FirstRunService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
 
@ExtendWith(MockitoExtension.class)
class FirstRunServiceTest {
 
    @Mock private UserRepoInterface userRepository;
    @Mock private RoleRepositoryInterface roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
 
    private FirstRunService service;
 
    @BeforeEach
    void setUp() {
        TransactionTemplate transactions = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new FirstRunService(userRepository, passwordEncoder, roleRepository, transactions);
    }
 
    private static SetupForm form() {
        SetupForm form = new SetupForm();
        form.setFirstName("  Ana ");
        form.setLastName("Silva");
        form.setEmail(" ana@example.com ");
        form.setPassword("Str0ng!Pass");
        form.setConfirmPassword("Str0ng!Pass");
        return form;
    }
 
    private Role givenAdministratorRole() {
        Role role = new Role();
        role.setRoleType(RoleType.ADMINISTRATOR);
        when(roleRepository.findByRoleType(RoleType.ADMINISTRATOR)).thenReturn(role);
        return role;
    }
 
    @Test
    void setupIsRequiredWhenNoUserExists() {
        when(userRepository.count()).thenReturn(0L);
 
        assertTrue(service.isSetupRequired());
    }
 
    @Test
    void setupIsNotRequiredWhenAUserExistsAndTheAnswerIsRemembered() {
        when(userRepository.count()).thenReturn(1L);
 
        assertFalse(service.isSetupRequired());
        assertFalse(service.isSetupRequired());
        verify(userRepository, times(1)).count();
    }
 
    @Test
    void createsAnActiveAdministratorWithAHashedPassword() {
        Role administratorRole = givenAdministratorRole();
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
 
        service.createFirstAdministrator(form());
 
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertSame(administratorRole, user.getRole());
        // assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals("hashed", user.getPassword());
        assertEquals("Ana", user.getFirstName());
        assertEquals("ana@example.com", user.getEmail());
    }
 
    @Test
    void setupIsNoLongerRequiredAfterTheAdministratorIsCreated() {
        givenAdministratorRole();
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
 
        service.createFirstAdministrator(form());
 
        assertFalse(service.isSetupRequired());
        verify(userRepository, times(1)).count(); // not asked again
    }
 
    @Test
    void refusesToCreateAnAdministratorWhenAUserAlreadyExists() {
        when(userRepository.count()).thenReturn(1L);
 
        assertThrows(SetupAlreadyCompletedException.class, () -> service.createFirstAdministrator(form()));
        verify(userRepository, never()).save(any());
    }
 
    @Test
    void failsClearlyWhenTheAdministratorRoleRowIsMissing() {
        when(userRepository.count()).thenReturn(0L);
        when(roleRepository.findByRoleType(RoleType.ADMINISTRATOR)).thenReturn(null);
 
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.createFirstAdministrator(form()));
 
        assertTrue(ex.getMessage().contains("ADMINISTRATOR"));
        verify(userRepository, never()).save(any());
    }
}
 
