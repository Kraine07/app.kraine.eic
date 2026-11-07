package app.kraine.eic.exception;


/** One field of a JSON request is not acceptable. The API answers 400 with the message under that field. */
public class FieldValidationException extends RuntimeException {
 
    private final String field;
 
    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
 
    public String getField() {
        return field;
    }
}
 