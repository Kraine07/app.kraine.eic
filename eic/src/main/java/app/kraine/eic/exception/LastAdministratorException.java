package app.kraine.eic.exception;


public class LastAdministratorException extends RuntimeException {
    public LastAdministratorException() {
        super("Cannot suspend or delete the last active Administrator.");
    }

}
