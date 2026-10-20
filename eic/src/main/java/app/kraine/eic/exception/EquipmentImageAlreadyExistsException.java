package app.kraine.eic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class EquipmentImageAlreadyExistsException extends RuntimeException {

    public EquipmentImageAlreadyExistsException(Long equipmentId) {
        super("This equipment already has an image.");
    }
}
