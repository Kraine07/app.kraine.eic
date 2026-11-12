package app.kraine.eic.propertyManagement;

import app.kraine.eic.exception.DuplicatePropertyNameException;
import app.kraine.eic.exception.InvalidPropertyReferenceException;
import app.kraine.eic.exception.PropertyLinkedToEquipmentException;
import app.kraine.eic.exception.PropertyNotFoundException;
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
 * JSON errors (RFC 7807) for the property API. The message is in "detail" and, for a field that was refused,
 * also in "errors" under the form's field name (name, regionId, editorId). 400 means fix the fields, 409 means
 * the property still has equipment, 404 means it no longer exists.
 */
@RestControllerAdvice(assignableTypes = app.kraine.eic.api.PropertyApiController.class)
public class PropertyApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return withErrors("Some fields are invalid. Correct them and try again.", errors);
    }

    @ExceptionHandler(DuplicatePropertyNameException.class)
    public ResponseEntity<ProblemDetail> duplicateName(DuplicatePropertyNameException ex) {
        return withErrors(ex.getMessage(), Map.of("name", "This property name is already in use."));
    }

    @ExceptionHandler(InvalidPropertyReferenceException.class)
    public ResponseEntity<ProblemDetail> invalidReference(InvalidPropertyReferenceException ex) {
        return withErrors(ex.getMessage(), Map.of(ex.getField(), ex.getMessage()));
    }

    @ExceptionHandler(PropertyLinkedToEquipmentException.class)
    public ResponseEntity<ProblemDetail> linkedToEquipment(PropertyLinkedToEquipmentException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PropertyNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(PropertyNotFoundException ex) {
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
