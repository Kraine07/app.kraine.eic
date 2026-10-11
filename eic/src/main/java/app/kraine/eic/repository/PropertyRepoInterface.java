package app.kraine.eic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import app.kraine.eic.model.Property;

public interface PropertyRepoInterface extends JpaRepository<Property, Long> {

    // @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Property p WHERE p.user.id = :userId")
    boolean existsByUserId(Long userId);

}
