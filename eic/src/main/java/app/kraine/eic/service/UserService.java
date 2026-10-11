package app.kraine.eic.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import app.kraine.eic.exception.DuplicateEmailException;
import app.kraine.eic.exception.UserLinkedToPropertyException;
import app.kraine.eic.exception.UserNotFoundException;
import app.kraine.eic.form.UserForm;
import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.RoleRepositoryInterface;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.security.TemporaryPasswordGenerator;
import app.kraine.eic.security.TemporaryPasswordMailer;
import app.kraine.eic.userManagement.LastAdministratorGuard;
import app.kraine.eic.userManagement.UserPropertyLinks;
import app.kraine.eic.userManagement.UserViews.RoleOption;
import app.kraine.eic.userManagement.UserViews.UserDashboard;
import app.kraine.eic.userManagement.UserViews.UserEditData;
import app.kraine.eic.userManagement.UserViews.UserRow;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * UC-02 to UC-06: the Administrator's user management. Every change is logged with the acting
 * Administrator (UA-18).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepoInterface userRepository;
    private final RoleRepositoryInterface roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final TemporaryPasswordMailer mailer;
    private final LastAdministratorGuard lastAdministratorGuard;
    private final ObjectProvider<UserPropertyLinks> propertyLinks;
    private final TransactionTemplate transactionTemplate;

    /**
     * Suspending and deleting check "is this the last active Administrator" and then change data. Two
     * Administrators doing this to each other at the same moment could otherwise both pass the check.
     * The lock covers one application instance; with several instances use a database lock instead.
     */
    private final Lock administratorLock = new ReentrantLock();

    @PostConstruct
    void warnWhenPropertyLinksAreMissing() {
        if (propertyLinks.getIfAvailable() == null) {
            log.warn("No UserPropertyLinks bean found: users assigned to a property can be deleted. "
                    + "Implement UserPropertyLinks to enforce UC-06 step 5.");
        }
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public UserDashboard dashboard() {
        List<User> users = userRepository.findAllWithDetails();
        List<UserRow> rows = users.stream()
                .sorted(Comparator.comparing(User::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(User::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toRow)
                .toList();
        long activeAdministrators = users.stream()
                .filter(u -> !u.getIsSuspended() && u.getRole().getRoleType() == RoleType.ADMINISTRATOR)
                .count();
        long suspended = users.stream().filter(u -> u.getIsSuspended()).count();
        return new UserDashboard(rows, users.size(), activeAdministrators, suspended);
    }

    @Transactional(readOnly = true)
    public UserEditData findForEdit(Long id) {
        User user = find(id);
        UserForm form = new UserForm();
        form.setFirstName(user.getFirstName());
        form.setLastName(user.getLastName());
        form.setEmail(user.getEmail());
        form.setRoleType(user.getRole().getRoleType());
        return new UserEditData(user.getId(), form, label(user.getRole().getRoleType()),
                user.getIsSuspended());
    }
 
    public List<RoleOption> roleOptions() {
        return Arrays.stream(RoleType.values()).map(type -> new RoleOption(type.name(), label(type))).toList();
    }
 
    // ------------------------------------------------------------------ UC-02 create
 
    /**
     * Steps 7-9. The email is sent inside the transaction: if it cannot be sent, the account is not
     * created, so there is never an account whose password nobody knows.
     */
    @Transactional
    public User createUser(UserForm form, String actor) {
        String email = form.getEmail().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }
        Role role = roleRepository.findByRoleType(form.getRoleType());
        if (role == null) {
            throw new IllegalStateException("The " + form.getRoleType() + " role does not exist.");
        }
 
        String temporaryPassword = passwordGenerator.generate();
        User user = new User();
        user.setFirstName(form.getFirstName().trim());
        user.setLastName(form.getLastName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setRole(role);
        user.setIsSuspended(false);
        user.setIsTemporaryPassword(true);
 
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException(email); // another request used the email a moment ago
        }
        mailer.sendTemporaryPassword(saved.getEmail(), saved.getFirstName(), temporaryPassword);
 
        log.info("User {} created by {} with role {}", saved.getEmail(), actor, form.getRoleType());
        return saved;
    }
 
    // ------------------------------------------------------------------ UC-03 update
 
    @Transactional
    public void updateUser(Long id, UserForm form, String actor) {
        User user = find(id);
        String email = form.getEmail().trim();
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new DuplicateEmailException(email);
        }
        user.setFirstName(form.getFirstName().trim());
        user.setLastName(form.getLastName().trim());
        user.setEmail(email);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException(email);
        }
        log.info("User {} updated by {}", user.getEmail(), actor);
    }
 
    // ------------------------------------------------------------------ UC-04 suspend
 
    public void suspend(Long id, String actor) {
        exclusively(() -> {
            User user = find(id);
            if (user.getIsSuspended()) {
                return;
            }
            lastAdministratorGuard.assertNotLastActiveAdministrator(user); // step 7
            user.setIsSuspended(true);                          // step 8
            userRepository.save(user);
            log.info("User {} suspended by {}", user.getEmail(), actor);   // step 9
        });
    }
 
    // ------------------------------------------------------------------ UC-05 activate
 
    /** Steps 7-9: enables the account and emails a new temporary password. */
    @Transactional
    public void activate(Long id, String actor) {
        User user = find(id);
        if (!user.getIsSuspended()) {
            return;
        }
        String temporaryPassword = passwordGenerator.generate();
        user.setIsSuspended(false);
        user.setFailedAttempts(0);
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setIsTemporaryPassword(true);
        userRepository.save(user);
        mailer.sendTemporaryPassword(user.getEmail(), user.getFirstName(), temporaryPassword);
        log.info("User {} activated by {}", user.getEmail(), actor);
    }
 
    // ------------------------------------------------------------------ UC-06 delete
 
    public void delete(Long id, String actor) {
        exclusively(() -> {
            User user = find(id);
            UserPropertyLinks links = propertyLinks.getIfAvailable();
            if (links != null && links.isLinkedToProperty(user.getId())) {     // step 5
                throw new UserLinkedToPropertyException("User " + user.getEmail() + " is linked to a property and cannot be deleted.");
            }
            lastAdministratorGuard.assertNotLastActiveAdministrator(user);     // step 6
            userRepository.delete(user);                                       // step 7
            log.info("User {} deleted by {}", user.getEmail(), actor);
        });
    }
 
    // ------------------------------------------------------------------ helpers
 
    private void exclusively(Runnable action) {
        administratorLock.lock();
        try {
            transactionTemplate.executeWithoutResult(status -> action.run());
        } finally {
            administratorLock.unlock();
        }
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException());
    }

    private UserRow toRow(User user) {
        return new UserRow(user.getId(), user.getFirstName() + " " + user.getLastName(), user.getEmail(),
                label(user.getRole().getRoleType()),  !user.getIsSuspended());
    }

    /** eg. ADMINISTRATOR becomes "Administrator". */
    static String label(Enum<?> value) {
        String text = value.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
