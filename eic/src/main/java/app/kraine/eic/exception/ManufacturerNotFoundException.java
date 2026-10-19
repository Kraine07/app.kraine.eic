package app.kraine.eic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ManufacturerNotFoundException extends RuntimeException {

    public ManufacturerNotFoundException(Long id) {
        super("Manufacturer " + id + " was not found.");
    }
}
