package app.kraine.eic.manufacturerManagement;

/**
 * OPTIONAL. UC-10 step 6 (MAN-4): tells the manufacturer screens whether anything still refers to a
 * manufacturer, so a clear message is shown before anything is deleted. Implement it as a bean, for example:
 *
 * <pre>
 * &#64;Component
 * class ManufacturerLinks implements ManufacturerEquipmentLinks {
 *     public boolean isLinked(Long manufacturerId) {
 *         return modelRepository.existsByManufacturerId(manufacturerId);
 *     }
 * }
 * </pre>
 *
 * Without a bean the database's foreign keys still refuse the delete, and the same message is shown.
 */
public interface ManufacturerEquipmentLinks {

    boolean isLinked(Long manufacturerId);
}
