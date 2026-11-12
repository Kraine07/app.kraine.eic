package app.kraine.eic.exception;

/** The "from" date is after the "to" date. The API answers 400 with the message under "from". */
public class InvalidAuditRangeException extends RuntimeException {

    public InvalidAuditRangeException(String message) {
        super(message);
    }
}
