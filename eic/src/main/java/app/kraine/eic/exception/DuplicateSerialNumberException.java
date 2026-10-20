package app.kraine.eic.exception;

/** UC-23 / UC-24 alternative flow (EQM-1, EQM-2): the serial number is already in use. */
public class DuplicateSerialNumberException extends RuntimeException {

    public DuplicateSerialNumberException(String serialNumber) {
        super("The serial number " + serialNumber + " is already in use.");
    }
}