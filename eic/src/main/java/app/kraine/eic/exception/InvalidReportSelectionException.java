package app.kraine.eic.exception;


/** The editor chosen for a report cannot be used. The message is safe to show on the page. */
public class InvalidReportSelectionException extends RuntimeException {
 
    public InvalidReportSelectionException(String message) {
        super(message);
    }
}
