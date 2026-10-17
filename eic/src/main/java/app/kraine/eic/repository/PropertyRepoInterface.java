package app.kraine.eic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.kraine.eic.model.Property;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;

public interface PropertyRepoInterface extends JpaRepository<Property, Long> {

    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByUserId(Long userId);            // already needed by PropertyUserLinks

    // UserRepoInterface
    @Query("SELECT u FROM User u WHERE u.role.roleType = :roleType AND u.isSuspended = false "
        + "ORDER BY u.lastName, u.firstName")
    List<User> findActiveByRoleType(@Param("roleType") RoleType roleType);


    List<Property> findByUserId(Long id);
}
