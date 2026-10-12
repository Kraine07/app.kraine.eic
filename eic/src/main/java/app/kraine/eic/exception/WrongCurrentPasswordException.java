package app.kraine.eic.exception;

/**
 * WrongCurrentPasswordException
 */
public class WrongCurrentPasswordException extends RuntimeException {
    public WrongCurrentPasswordException() {
        super("The current password you entered is incorrect.");
    }

}
