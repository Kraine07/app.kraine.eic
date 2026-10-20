package app.kraine.eic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// If your project already has this exception, delete this file and reuse yours.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EquipmentNotFoundException extends RuntimeException {

    public EquipmentNotFoundException(Long equipmentId) {
        super("Equipment " + equipmentId + " was not found.");
    }
}
