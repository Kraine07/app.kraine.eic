package app.kraine.eic.audit;

import app.kraine.eic.api.AuditApiController;
import app.kraine.eic.exception.InvalidAuditRangeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * JSON errors (RFC 7807) for the audit API. The message is in "detail" and the field it belongs to in "errors":
 * the range problem under "from", and a value that is not a date or not a known event type under its own name.
 */
@RestControllerAdvice(assignableTypes = AuditApiController.class)
public class AuditApiExceptionHandler {

    @ExceptionHandler(InvalidAuditRangeException.class)
    public ResponseEntity<ProblemDetail> invalidRange(InvalidAuditRangeException ex) {
        return withErrors(ex.getMessage(), Map.of("from", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> invalidValue(MethodArgumentTypeMismatchException ex) {
        String message = switch (ex.getName()) {
            case "from", "to" -> "Enter the date as YYYY-MM-DD.";
            case "type" -> "That is not a known event type.";
            default -> "That value is not valid.";
        };
        return withErrors(message, Map.of(ex.getName(), message));
    }

    private static ResponseEntity<ProblemDetail> withErrors(String detail, Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
