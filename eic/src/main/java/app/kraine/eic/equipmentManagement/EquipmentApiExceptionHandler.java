package app.kraine.eic.equipmentManagement;

import app.kraine.eic.api.EquipmentApiController;
import app.kraine.eic.exception.DuplicateSerialNumberException;
import app.kraine.eic.exception.EquipmentInUseException;
import app.kraine.eic.exception.EquipmentNotFoundException;
import app.kraine.eic.exception.InvalidEquipmentReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON errors (RFC 7807) for the equipment API. The message is in "detail" and, for a field that was refused,
 * also in "errors" under the form's field name (serialNumber, manufacturedDate, modelKey, locationKey). 400
 * means fix the fields, 409 means other records still refer to the equipment, 404 means it no longer exists.
 */
@RestControllerAdvice(assignableTypes = EquipmentApiController.class)
public class EquipmentApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return withErrors("Some fields are invalid. Correct them and try again.", errors);
    }

    @ExceptionHandler(DuplicateSerialNumberException.class)
    public ResponseEntity<ProblemDetail> duplicateSerialNumber(DuplicateSerialNumberException ex) {
        return withErrors(ex.getMessage(), Map.of("serialNumber", "This serial number is already in use."));
    }

    @ExceptionHandler(InvalidEquipmentReferenceException.class)
    public ResponseEntity<ProblemDetail> invalidReference(InvalidEquipmentReferenceException ex) {
        return withErrors(ex.getMessage(), Map.of(ex.getField(), ex.getMessage()));
    }

    @ExceptionHandler(EquipmentInUseException.class)
    public ResponseEntity<ProblemDetail> inUse(EquipmentInUseException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EquipmentNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(EquipmentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    private static ResponseEntity<ProblemDetail> withErrors(String detail, Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
