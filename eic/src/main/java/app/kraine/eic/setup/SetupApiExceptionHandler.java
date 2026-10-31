package app.kraine.eic.setup;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.kraine.eic.exception.SetupAlreadyCompletedException;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON errors (RFC 7807) for the setup API. The message is in "detail", per-field messages in "errors". */
@RestControllerAdvice(assignableTypes = SetupApiController.class)
public class SetupApiExceptionHandler {

    @ExceptionHandler(SetupAlreadyCompletedException.class)
    public ResponseEntity<ProblemDetail> alreadyCompleted(SetupAlreadyCompletedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Some fields are invalid. Correct them and try again.");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
