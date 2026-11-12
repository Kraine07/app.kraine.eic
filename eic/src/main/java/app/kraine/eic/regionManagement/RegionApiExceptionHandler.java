package app.kraine.eic.regionManagement;

import app.kraine.eic.exception.DuplicateRegionNameException;
import app.kraine.eic.exception.RegionInUseException;
import app.kraine.eic.exception.RegionNotFoundException;
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
 * JSON errors (RFC 7807) for the region API. The message is in "detail" and, for the name, also in "errors".
 * 400 means fix the name, 409 means a property still belongs to the region, 404 means it no longer exists.
 */
@RestControllerAdvice(assignableTypes = app.kraine.eic.api.RegionApiController.class)
public class RegionApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return withErrors("Some fields are invalid. Correct them and try again.", errors);
    }

    @ExceptionHandler(DuplicateRegionNameException.class)
    public ResponseEntity<ProblemDetail> duplicateName(DuplicateRegionNameException ex) {
        return withErrors(ex.getMessage(), Map.of("name", "This region name is already in use."));
    }

    @ExceptionHandler(RegionInUseException.class)
    public ResponseEntity<ProblemDetail> inUse(RegionInUseException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RegionNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(RegionNotFoundException ex) {
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
