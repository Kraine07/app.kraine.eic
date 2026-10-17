package app.kraine.eic.service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import app.kraine.eic.model.User;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.repository.UserRepoInterface;
import jakarta.transaction.Transactional;
import app.kraine.eic.repository.RoleRepositoryInterface;
import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.exception.SetupAlreadyCompletedException;
import app.kraine.eic.form.SetupForm;



/**
 * First-run setup: finds out whether any user exists (UA-19) and creates the first
 * administrator (UA-21).
 *
 * <p>Written against these assumptions about your code. Adjust the names if yours differ:
 * <ul>
 *   <li>{@code UserRepository extends JpaRepository<User, Long>}</li>
 *   <li>{@code User} has a no-args constructor, getters and setters for firstName, lastName, email,
 *       password (the hash), role, status and passwordTemporary (true means "must change on first login")</li>
 *   <li>{@code Role.ADMINISTRATOR} and {@code UserStatus.ACTIVE}</li>
 *   <li>a {@code PasswordEncoder} bean (for example BCrypt)</li>
 * </ul>
 */
@Slf4j
@Service
public class FirstRunService {

    private final UserRepoInterface userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepositoryInterface roleRepository;
    private final TransactionTemplate transactionTemplate;
    private final AuditService auditService;

    public FirstRunService(
            UserRepoInterface userRepository,
            PasswordEncoder passwordEncoder,
            RoleRepositoryInterface roleRepository,
            TransactionTemplate transactionTemplate,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.transactionTemplate = transactionTemplate;
        this.auditService = auditService;
    }

    private final Lock setupLock = new ReentrantLock();

    /** Once a user has been seen, setup can never be required again, so the answer is remembered. */
    private final AtomicBoolean usersExist = new AtomicBoolean(false);

    /** UA-19: true while the system has no user account at all. */
    public boolean isSetupRequired() {
        if (usersExist.get()) {
            return false;
        }
        if (userRepository.count() > 0) {
            usersExist.set(true);
            return false;
        }
        return true;
    }

    /**
     * UA-20 to UA-22: creates the first account, as an active Administrator, and only while no user
     * exists. The check and the insert happen together under a lock, so two simultaneous requests
     * cannot both create an administrator.
     *
     * <p>The lock protects a single application instance. If you run several instances against one
     * database, replace it with a database lock.
     *
     * @throws SetupAlreadyCompletedException if a user account already exists
     */

    @Transactional
    public User createFirstAdministrator(SetupForm form) {
        setupLock.lock();
        try {
            User saved = transactionTemplate.execute(status -> {
                if (userRepository.count() > 0) {
                    usersExist.set(true);
                    throw new SetupAlreadyCompletedException("Setup already completed: user accounts exist.");
                }
                auditService.record(AuditEventType.FIRST_ADMIN_CREATED, "system",  form.getEmail(), "First admin user created.");
                return userRepository.save(newAdministrator(form));
            });
            usersExist.set(true);
            log.info("First administrator account created for {}", saved.getEmail());
            return saved;
        } finally {

            setupLock.unlock();
        }
    }

    private User newAdministrator(SetupForm form) {
        User user = new User();
        user.setFirstName(form.getFirstName().trim());
        user.setLastName(form.getLastName().trim());
        user.setEmail(form.getEmail().trim());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRole(roleRepository.findByRoleType(RoleType.ADMINISTRATOR));
        user.setIsSuspended(false);
        // UA-21: the administrator chose this password, so no forced change on first login (UA-9).
        user.setIsTemporaryPassword(false);
        user.setFailedAttempts(0);
        return user;
    }
}
