package app.kraine.eic.exception;

/** Thrown when the selected file fails validation. The message is safe to show to the user. */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
