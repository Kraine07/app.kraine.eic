package app.kraine.eic.locationManagement;

import app.kraine.eic.api.LocationApiController;
import app.kraine.eic.exception.DuplicateLocationNameException;
import app.kraine.eic.exception.InvalidLocationReferenceException;
import app.kraine.eic.exception.LocationLinkedToEquipmentException;
import app.kraine.eic.exception.LocationNotFoundException;
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
 * JSON errors (RFC 7807) for the location API. The message is in "detail" and, for a field that was refused,
 * also in "errors" under the form's field name (propertyId, name, originalName). 400 means fix the fields,
 * 409 means equipment is still placed at the location, 404 means it no longer exists. A refusal because the
 * location belongs to someone else's property is a 403, produced by the security layer.
 */
@RestControllerAdvice(assignableTypes = LocationApiController.class)
public class LocationApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return withErrors("Some fields are invalid. Correct them and try again.", errors);
    }

    @ExceptionHandler(DuplicateLocationNameException.class)
    public ResponseEntity<ProblemDetail> duplicateName(DuplicateLocationNameException ex) {
        return withErrors(ex.getMessage(), Map.of("name", "This property already has a location with this name."));
    }

    @ExceptionHandler(InvalidLocationReferenceException.class)
    public ResponseEntity<ProblemDetail> invalidReference(InvalidLocationReferenceException ex) {
        return withErrors(ex.getMessage(), Map.of(ex.getField(), ex.getMessage()));
    }

    @ExceptionHandler(LocationLinkedToEquipmentException.class)
    public ResponseEntity<ProblemDetail> linkedToEquipment(LocationLinkedToEquipmentException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(LocationNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(LocationNotFoundException ex) {
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
