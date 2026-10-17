package app.kraine.eic.repository;

import java.util.List;
import java.util.Optional;

import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.transaction.Transactional;



@Transactional
public interface UserRepoInterface extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u " +
        "LEFT JOIN FETCH u.role "
    )
    List<User> findAllWithDetails();

    User findByEmail(String email);

    Boolean existsByEmail(String email);

    // login and lockout look accounts up by the email typed on the form, in any letter case
    Optional<User> findByEmailIgnoreCase(String email);

    // duplicate-email checks when creating and editing a user
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    // how many active Administrators exist (UA-23, UA-24)
    @Query("SELECT COUNT(u) FROM User u WHERE u.role.roleType = :roleType AND u.isSuspended = :isSuspended")
    long countByRoleTypeAndStatus(@Param("roleType") RoleType roleType, @Param("isSuspended") boolean isSuspended);

    List<User> findActiveByRoleRoleType(RoleType roleType);
}