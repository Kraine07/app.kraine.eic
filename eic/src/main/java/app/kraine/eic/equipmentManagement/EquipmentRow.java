package app.kraine.eic.equipmentManagement;

import java.time.YearMonth;

/**
 * One line of the equipment list. The list is read with a query that selects only these columns, so
 * the image bytes that Equipment carries are never loaded for a whole page of rows. It is a top-level
 * record because the query names it with new com.example.equipmentinventory.equipment.EquipmentRow(...).
 */
public record EquipmentRow(Long id, String serialNumber, YearMonth manufacturedDate, String manufacturerName,
                        String modelDescription, String propertyName, String locationName) {
}
