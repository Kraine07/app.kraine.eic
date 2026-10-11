package app.kraine.eic.exception;

public class SetupAlreadyCompletedException extends RuntimeException {
    public SetupAlreadyCompletedException(String message) {
        super(message);
    }
}
