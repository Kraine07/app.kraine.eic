package app.kraine.eic.exception;

/** UC-13 alternative flow (MOD-4), and changing a model: equipment still uses this model. */
public class ModelLinkedToEquipmentException extends RuntimeException {

    private ModelLinkedToEquipmentException(String message) {
        super(message);
    }

    public static ModelLinkedToEquipmentException forDelete() {
        return new ModelLinkedToEquipmentException("This model is linked to equipment. Remove the link first.");
    }

    /**
     * A model is identified by its manufacturer and description, which equipment points to, so a model
     * that equipment uses cannot have either of them changed.
     */
    public static ModelLinkedToEquipmentException forChange() {
        return new ModelLinkedToEquipmentException(
                "This model is linked to equipment, so its manufacturer and description cannot be changed.");
    }
}
