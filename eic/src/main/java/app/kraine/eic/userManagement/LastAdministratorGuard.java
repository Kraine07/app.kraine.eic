package app.kraine.eic.userManagement;

import org.springframework.stereotype.Component;

import app.kraine.eic.exception.LastAdministratorException;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.UserRepoInterface;
import lombok.RequiredArgsConstructor;

/** UA-23/UA-24: there must always be at least one active Administrator. */
@Component
@RequiredArgsConstructor
public class LastAdministratorGuard  {
    private final UserRepoInterface userRepository;

    /** Call before suspending or deleting [target] (or changing its role). */
    public void assertNotLastActiveAdministrator(User target) {
        boolean activeAdministrator = !target.getIsSuspended()
                && target.getRole().getRoleType() == RoleType.ADMINISTRATOR;
        if (activeAdministrator
                && userRepository.countByRoleTypeAndStatus(RoleType.ADMINISTRATOR, !target.getIsSuspended()) <= 1) {
            throw new LastAdministratorException();
        }
    }
}
