package app.kraine.eic.exception;

/** UC-08 step 7 / UC-09 step 7 (MAN-2, MAN-3): another manufacturer already has this name. */
public class DuplicateManufacturerNameException extends RuntimeException {

    public DuplicateManufacturerNameException(String name) {
        super("The manufacturer name " + name + " is already in use.");
    }
}
