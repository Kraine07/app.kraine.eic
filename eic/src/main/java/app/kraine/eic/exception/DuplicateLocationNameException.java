package app.kraine.eic.exception;

/** UC-20 / UC-21 (LM-1, LM-2): the property already has a location with this name. */
public class DuplicateLocationNameException extends RuntimeException {

    public DuplicateLocationNameException(String name, String propertyName) {
        super("The property " + propertyName + " already has a location named " + name + ".");
    }
}
