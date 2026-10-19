package app.kraine.eic.exception;

/** UC-10 alternative flow (MAN-4): equipment, or models that equipment uses, still refer to this manufacturer. */
public class ManufacturerInUseException extends RuntimeException {

    public ManufacturerInUseException() {
        super("This manufacturer is linked to equipment or models. Remove the link first.");
    }
}
