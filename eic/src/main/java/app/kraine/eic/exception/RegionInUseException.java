package app.kraine.eic.exception;


/** UC-16 alternative flow (RM-4): a property still belongs to this region. */
public class RegionInUseException extends RuntimeException {
 
    public RegionInUseException() {
        super("This region is linked to a property. Move or delete those properties first.");
    }
}
 