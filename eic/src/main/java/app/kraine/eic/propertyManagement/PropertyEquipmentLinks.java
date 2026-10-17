package app.kraine.eic.propertyManagement;

/**
 * OPTIONAL. UC-19 step 5: tells the property screens whether equipment is placed at a property, so
 * a clear message is shown before anything is deleted. Implement it as a bean, for example:
 *
 * <pre>
 * &#64;Component
 * class EquipmentPropertyLinks implements PropertyEquipmentLinks {
 *     public boolean isLinkedToEquipment(Long propertyId) { return equipmentRepository.existsByLocationPropertyId(propertyId); }
 * }
 * </pre>
 *
 * Without a bean the database's foreign key still refuses the delete, and the same message is shown.
 */
public interface PropertyEquipmentLinks {

    boolean isLinkedToEquipment(Long propertyId);
}
