package app.kraine.eic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.kraine.eic.equipmentManagement.EquipmentRow;
import app.kraine.eic.model.Equipment;

public interface EquipmentRepoInterface extends JpaRepository<Equipment, Long> {

    boolean existsBySerialNumberIgnoreCase(String serialNumber);
    boolean existsBySerialNumberIgnoreCaseAndIdNot(String serialNumber, Long id);
    
    // "is it in use?" checks, used by EquipmentLinkConfig
    boolean existsByLocationPropertyId(Long propertyId);
    boolean existsByLocationPropertyIdAndLocationName(Long propertyId, String locationName);
    boolean existsByModelManufacturerId(Long manufacturerId);
    boolean existsByModelManufacturerIdAndModelDescription(Long manufacturerId, String description);
    
    // The list page. It selects only the columns shown, so the image bytes that Equipment carries are
    // never loaded for a whole page of rows. :q is already trimmed and lower-case ("" means no search).
    String ROW = "SELECT new app.kraine.eic.equipmentManagement.EquipmentRow("
               + "e.id, e.serialNumber, e.manufacturedDate, mf.name, m.description, p.name, l.name) ";
    String FROM = "FROM Equipment e JOIN e.model m JOIN m.manufacturer mf JOIN e.location l JOIN l.property p ";
    String SEARCH = "(:q = '' OR LOWER(COALESCE(e.serialNumber, '')) LIKE CONCAT('%', :q, '%') "
                  + "OR LOWER(m.description) LIKE CONCAT('%', :q, '%') OR LOWER(mf.name) LIKE CONCAT('%', :q, '%') "
                  + "OR LOWER(l.name) LIKE CONCAT('%', :q, '%') OR LOWER(p.name) LIKE CONCAT('%', :q, '%'))";
    String ORDER = " ORDER BY LOWER(p.name), LOWER(l.name), LOWER(mf.name), LOWER(m.description), e.id";
    
    @Query(value = ROW + FROM + "WHERE " + SEARCH + ORDER,
           countQuery = "SELECT COUNT(e) " + FROM + "WHERE " + SEARCH)
    Page<EquipmentRow> search(@Param("q") String q, Pageable pageable);
    
    @Query(value = ROW + FROM + "WHERE p.user.id = :userId AND " + SEARCH + ORDER,
           countQuery = "SELECT COUNT(e) " + FROM + "WHERE p.user.id = :userId AND " + SEARCH)
    Page<EquipmentRow> searchForEditor(@Param("q") String q, @Param("userId") Long userId, Pageable pageable);

}
