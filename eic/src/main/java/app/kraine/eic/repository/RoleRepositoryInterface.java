package app.kraine.eic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import jakarta.transaction.Transactional;

/**
 * RoleRepositoryInterface
 */

@Transactional
public interface RoleRepositoryInterface extends JpaRepository<Role, Long>  {

    Role findByRoleType(RoleType roleType);

}
