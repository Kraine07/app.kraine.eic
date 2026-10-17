package app.kraine.eic.exception;

/** UC-22 alternative flow (LM-3), and renaming: equipment is still placed at this location. */
public class LocationLinkedToEquipmentException extends RuntimeException {

    private LocationLinkedToEquipmentException(String message) {
        super(message);
    }

    public static LocationLinkedToEquipmentException forDelete() {
        return new LocationLinkedToEquipmentException(
                "This location has equipment. Move or remove the equipment first.");
    }

    /**
     * A location's name is part of its primary key, which equipment points to, so a location that has
     * equipment cannot be renamed.
     */
    public static LocationLinkedToEquipmentException forRename() {
        return new LocationLinkedToEquipmentException(
                "This location has equipment, so it cannot be renamed. Move the equipment first.");
    }
}
