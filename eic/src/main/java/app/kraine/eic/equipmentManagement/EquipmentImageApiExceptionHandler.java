package app.kraine.eic.equipmentManagement;

import app.kraine.eic.api.EquipmentImageApiController;
import app.kraine.eic.exception.EquipmentImageAlreadyExistsException;
import app.kraine.eic.exception.EquipmentNotFoundException;
import app.kraine.eic.exception.InvalidImageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the use case's exceptions into JSON error bodies (RFC 7807). The message is in "detail". */
@RestControllerAdvice(assignableTypes = EquipmentImageApiController.class)
public class EquipmentImageApiExceptionHandler {

    /** Alternative flow: the message names the allowed file types and the maximum size. */
    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ProblemDetail> invalidImage(InvalidImageException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(EquipmentImageAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> alreadyExists(EquipmentImageAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EquipmentNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(EquipmentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
