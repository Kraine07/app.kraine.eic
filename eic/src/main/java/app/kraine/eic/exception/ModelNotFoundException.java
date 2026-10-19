package app.kraine.eic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ModelNotFoundException extends RuntimeException {

    public ModelNotFoundException(Long manufacturerId, String description) {
        super("Model " + description + " of manufacturer " + manufacturerId + " was not found.");
    }
}
