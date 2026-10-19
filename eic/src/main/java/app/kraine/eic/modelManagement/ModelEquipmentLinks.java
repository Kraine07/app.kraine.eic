package app.kraine.eic.modelManagement;

/**
 * OPTIONAL. UC-13 step 5 (MOD-4): tells the model screens whether equipment uses a model, so a clear
 * message is shown before anything is changed. Implement it as a bean, for example:
 *
 * <pre>
 * &#64;Component
 * class EquipmentModelLinks implements ModelEquipmentLinks {
 *     public boolean isLinkedToEquipment(Long manufacturerId, String description) {
 *         return equipmentRepository.existsByModelManufacturerIdAndModelDescription(manufacturerId, description);
 *     }
 * }
 * </pre>
 *
 * Without a bean the database's foreign key still refuses the change, and the same message is shown.
 */
public interface ModelEquipmentLinks {

    boolean isLinkedToEquipment(Long manufacturerId, String description);
}
