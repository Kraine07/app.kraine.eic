package app.kraine.eic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.kraine.eic.model.Model;
import app.kraine.eic.model.ModelId;

public interface ModelRepoInterface extends JpaRepository<Model, ModelId> {


    boolean existsByManufacturerId(Long manufacturerId);
boolean existsByManufacturerIdAndDescriptionIgnoreCase(Long manufacturerId, String description);

@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(value = "UPDATE model SET manufacturer_id = :newManufacturerId, description = :newDescription "
             + "WHERE manufacturer_id = :oldManufacturerId AND description = :oldDescription",
       nativeQuery = true)
int updateKey(@Param("oldManufacturerId") Long oldManufacturerId, @Param("oldDescription") String oldDescription,
              @Param("newManufacturerId") Long newManufacturerId, @Param("newDescription") String newDescription);

@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("DELETE FROM Model m WHERE m.manufacturer.id = :manufacturerId AND m.description = :description")
int removeByKey(@Param("manufacturerId") Long manufacturerId, @Param("description") String description);
    // ModelRepoInterface extends JpaRepository<Model, ModelId>
// boolean existsByManufacturerIdAndDescriptionIgnoreCase(Long manufacturerId, String description);

// @Modifying(flushAutomatically = true, clearAutomatically = true)
// @Query(value = "UPDATE model SET manufacturer_id = :newManufacturerId, description = :newDescription "
//             + "WHERE manufacturer_id = :oldManufacturerId AND description = :oldDescription",
//     nativeQuery = true)
// int updateKey(@Param("oldManufacturerId") Long oldManufacturerId, @Param("oldDescription") String oldDescription,
//             @Param("newManufacturerId") Long newManufacturerId, @Param("newDescription") String newDescription);

// @Modifying(flushAutomatically = true, clearAutomatically = true)
// @Query("DELETE FROM Model m WHERE m.manufacturer.id = :manufacturerId AND m.description = :description")
// int removeByKey(@Param("manufacturerId") Long manufacturerId, @Param("description") String description);
}