package app.kraine.eic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.kraine.eic.model.Location;
import app.kraine.eic.model.LocationId;

public interface LocationRepoInterface extends JpaRepository<Location,LocationId> {
    // LocationRepoInterface extends JpaRepository<Location, LocationId>
List<Location> findByPropertyUserId(Long userId);
boolean existsByPropertyIdAndNameIgnoreCase(Long propertyId, String name);

@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(value = "UPDATE location SET name = :newName WHERE property_id = :propertyId AND name = :oldName",
    nativeQuery = true)
int rename(@Param("propertyId") Long propertyId, @Param("oldName") String oldName,
        @Param("newName") String newName);

// PropertyRepoInterface
// List<Property> findByUserId(Long userId);

Optional<Location> findById(LocationId id);
}

