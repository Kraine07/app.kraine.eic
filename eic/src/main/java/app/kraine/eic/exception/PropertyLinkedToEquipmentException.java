package app.kraine.eic.exception;

/** UC-19 step 5 (PM-4): equipment is still placed at this property. */
public class PropertyLinkedToEquipmentException extends RuntimeException {

    public PropertyLinkedToEquipmentException() {
        super("This property has equipment. Move or remove the equipment first.");
    }
}
