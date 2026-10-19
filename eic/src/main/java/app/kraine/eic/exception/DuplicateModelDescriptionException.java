package app.kraine.eic.exception;

/** UC-11 / UC-12 (MOD-2, MOD-3): the manufacturer already has a model with this description. */
public class DuplicateModelDescriptionException extends RuntimeException {

    public DuplicateModelDescriptionException(String description, String manufacturerName) {
        super("The manufacturer " + manufacturerName + " already has a model described as " + description + ".");
    }
}
