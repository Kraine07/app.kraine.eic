package app.kraine.eic.modelManagement;

import app.kraine.eic.api.ModelApiController;
import app.kraine.eic.exception.DuplicateModelDescriptionException;
import app.kraine.eic.exception.InvalidModelReferenceException;
import app.kraine.eic.exception.ModelLinkedToEquipmentException;
import app.kraine.eic.exception.ModelNotFoundException;
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
 * JSON errors (RFC 7807) for the model API. The message is in "detail" and, for a field that was refused, also
 * in "errors" under the form's field name (manufacturerId, description). 400 means fix the fields, 409 means
 * equipment still uses the model, 404 means it no longer exists.
 */
@RestControllerAdvice(assignableTypes = ModelApiController.class)
public class ModelApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return withErrors("Some fields are invalid. Correct them and try again.", errors);
    }

    @ExceptionHandler(DuplicateModelDescriptionException.class)
    public ResponseEntity<ProblemDetail> duplicateDescription(DuplicateModelDescriptionException ex) {
        return withErrors(ex.getMessage(),
                Map.of("description", "This manufacturer already has a model with this description."));
    }

    @ExceptionHandler(InvalidModelReferenceException.class)
    public ResponseEntity<ProblemDetail> invalidReference(InvalidModelReferenceException ex) {
        return withErrors(ex.getMessage(), Map.of(ex.getField(), ex.getMessage()));
    }

    @ExceptionHandler(ModelLinkedToEquipmentException.class)
    public ResponseEntity<ProblemDetail> linkedToEquipment(ModelLinkedToEquipmentException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ModelNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(ModelNotFoundException ex) {
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
