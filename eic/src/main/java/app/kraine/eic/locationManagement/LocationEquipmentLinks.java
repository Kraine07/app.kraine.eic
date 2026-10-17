package app.kraine.eic.locationManagement;

/**
 * OPTIONAL. UC-22 step 5 (LM-3): tells the location screens whether equipment is placed at a
 * location, so a clear message is shown before anything is changed. Implement it as a bean, for example:
 *
 * <pre>
 * &#64;Component
 * class EquipmentLocationLinks implements LocationEquipmentLinks {
 *     public boolean isLinkedToEquipment(Long propertyId, String locationName) {
 *         return equipmentRepository.existsByLocationPropertyIdAndLocationName(propertyId, locationName);
 *     }
 * }
 * </pre>
 *
 * Without a bean the database's foreign key still refuses the change, and the same message is shown.
 */
public interface LocationEquipmentLinks {

    boolean isLinkedToEquipment(Long propertyId, String locationName);
}
