package app.kraine.eic.exception;

public class LoginAttemptsExhaustedException extends RuntimeException {
    public LoginAttemptsExhaustedException(Throwable cause) {
        super(cause);
    }

}
