package app.kraine.eic.exception;

/** The selected manufacturer cannot be used. The field name tells the page where to show the message. */
public class InvalidModelReferenceException extends RuntimeException {

    private final String field;

    public InvalidModelReferenceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
