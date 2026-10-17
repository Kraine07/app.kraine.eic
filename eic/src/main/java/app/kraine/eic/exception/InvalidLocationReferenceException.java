package app.kraine.eic.exception;

/** The selected property cannot be used. The field name tells the page where to show the message. */
public class InvalidLocationReferenceException extends RuntimeException {

    private final String field;

    public InvalidLocationReferenceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
