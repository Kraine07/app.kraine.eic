package app.kraine.eic.exception;


/** The selected model or location, or the manufactured date, cannot be used. The field name says where to show it. */
public class InvalidEquipmentReferenceException extends RuntimeException {
 
    private final String field;
 
    public InvalidEquipmentReferenceException(String field, String message) {
        super(message);
        this.field = field;
    }
 
    public String getField() {
        return field;
    }
}
 