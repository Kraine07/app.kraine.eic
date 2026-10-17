package app.kraine.eic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class LocationNotFoundException extends RuntimeException {

    public LocationNotFoundException(Long propertyId, String name) {
        super("Location " + name + " of property " + propertyId + " was not found.");
    }
}
