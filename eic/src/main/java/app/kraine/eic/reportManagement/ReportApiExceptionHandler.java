package app.kraine.eic.reportManagement;

import app.kraine.eic.api.ReportApiController;
import app.kraine.eic.exception.InvalidReportSelectionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * JSON errors (RFC 7807) for the report API. A choice that cannot be used (an editor or property or region that no
 * longer exists, or a user who is not an editor) is a 400 whose message is in "detail" and under "selection". A report
 * the user is not allowed to see is a 403, produced by the security layer.
 */
@RestControllerAdvice(assignableTypes = ReportApiController.class)
public class ReportApiExceptionHandler {

    @ExceptionHandler(InvalidReportSelectionException.class)
    public ResponseEntity<ProblemDetail> invalidSelection(InvalidReportSelectionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setProperty("errors", Map.of("selection", ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
