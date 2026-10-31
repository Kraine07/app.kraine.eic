package app.kraine.eic.setup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.repository.RoleRepositoryInterface;

/**
 * Makes sure a Role row exists for every RoleType, so the first administrator (and every user created
 * later) can be given a role. Safe to run on every start: only missing roles are added.
 */
@Slf4j
@Component
@Order(1) // before FirstRunInitializer
@RequiredArgsConstructor
public class RoleInitializer implements ApplicationRunner {

    private final RoleRepositoryInterface roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (RoleType type : RoleType.values()) {
            if (!roleRepository.existsByRoleType(type)) {
                Role role = new Role();
                role.setRoleType(type);
                roleRepository.save(role);
                log.info("Created role {}", type);
            }
        }
    }
}
