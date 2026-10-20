package app.kraine.eic.exception;


/** Other records (for example highlights) still refer to this equipment, so the database refuses the delete. */
public class EquipmentInUseException extends RuntimeException {
 
    public EquipmentInUseException() {
        super("This equipment is referred to by other records, such as highlights. Remove them first.");
    }
}
 