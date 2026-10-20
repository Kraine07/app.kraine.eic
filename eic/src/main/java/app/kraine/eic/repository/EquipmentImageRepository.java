package app.kraine.eic.repository;

import app.kraine.eic.model.EquipmentImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EquipmentImageRepository extends JpaRepository<EquipmentImage, Long> {

    boolean existsByEquipmentId(Long equipmentId);

    /** Id and name only, so the image bytes are not loaded. */
    @Query("select i.id as id, i.imageName as imageName from EquipmentImage i where i.equipment.id = :equipmentId")
    Optional<ImageInfo> findInfoByEquipmentId(@Param("equipmentId") Long equipmentId);

    interface ImageInfo {
        Long getId();

        String getImageName();
    }
}
