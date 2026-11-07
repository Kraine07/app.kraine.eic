package app.kraine.eic.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.kraine.eic.exception.DuplicateEmailException;
import app.kraine.eic.exception.EmailDeliveryException;
import app.kraine.eic.exception.FieldValidationException;
import app.kraine.eic.exception.LastAdministratorException;
import app.kraine.eic.exception.UserLinkedToPropertyException;
import app.kraine.eic.exception.UserNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON errors (RFC 7807) for the user management API. The message is in "detail" and, for a field that was
 * refused, also in "errors". 400 means fix the fields, 409 means the action is not allowed right now, and 502
 * means the temporary-password email could not be sent (so nothing was changed).
 */
@RestControllerAdvice(assignableTypes = UserManagementApiController.class)
public class UserManagementApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return withErrors("Some fields are invalid. Correct them and try again.", errors);
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ProblemDetail> invalidField(FieldValidationException ex) {
        return withErrors(ex.getMessage(), Map.of(ex.getField(), ex.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ProblemDetail> duplicateEmail(DuplicateEmailException ex) {
        return withErrors(ex.getMessage(), Map.of("email", "This email is already in use."));
    }

    @ExceptionHandler({LastAdministratorException.class, UserLinkedToPropertyException.class})
    public ResponseEntity<ProblemDetail> notAllowed(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ProblemDetail> emailNotSent(EmailDeliveryException ex) {
        return problem(HttpStatus.BAD_GATEWAY,
                "The temporary password could not be emailed, so no changes were made. " + ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(UserNotFoundException ex) {
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
