package app.kraine.eic.exception;

/** UC-17 step 7 / UC-18 step 7: another property already has this name. */
public class DuplicatePropertyNameException extends RuntimeException {

    public DuplicatePropertyNameException(String name) {
        super("The property name " + name + " is already in use.");
    }
}
