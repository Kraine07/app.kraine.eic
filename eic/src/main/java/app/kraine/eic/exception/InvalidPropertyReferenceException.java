package app.kraine.eic.exception;

/** The selected region or editor cannot be used. The field name tells the page where to show the message. */
public class InvalidPropertyReferenceException extends RuntimeException {

    private final String field;

    public InvalidPropertyReferenceException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** "regionId" or "editorId", matching the form fields. */
    public String getField() {
        return field;
    }
}
