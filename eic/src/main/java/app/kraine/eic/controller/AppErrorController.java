package app.kraine.eic.controller;

import java.util.Date;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;




/**
 * OPTIONAL. Replaces Spring Boot's built-in error controller. Browsers get templates/error.html;
 * API and Flutter requests get a JSON problem body. Delete this file to go back to the default.
 */
@Slf4j
@Controller
public class AppErrorController implements ErrorController {
 
    static final String ERROR_VIEW = "error";
 
    /** What browsers (Accept: text/html) receive. */
    @RequestMapping(value = "/error", produces = MediaType.TEXT_HTML_VALUE)
    public String errorPage(HttpServletRequest request, HttpServletResponse response, Model model) {
        int status = statusOf(request);
        logServerError(request, status);
 
        response.setStatus(status);
        model.addAttribute("status", status);
        model.addAttribute("error", HttpStatus.valueOf(status).getReasonPhrase());
        model.addAttribute("path", requestedPath(request));
        model.addAttribute("timestamp", new Date());
        return ERROR_VIEW;
    }
 
    /** What everything else receives. Never contains the exception message or a stack trace. */
    @RequestMapping("/error")
    public ResponseEntity<ProblemDetail> errorJson(HttpServletRequest request) {
        int status = statusOf(request);
        logServerError(request, status);
 
        HttpStatus httpStatus = HttpStatus.valueOf(status);
        String detail = httpStatus.is5xxServerError() ? "An unexpected error occurred." : httpStatus.getReasonPhrase();
        return ResponseEntity.status(httpStatus).body(ProblemDetail.forStatusAndDetail(httpStatus, detail));
    }
 
    /** The status the container recorded, or 500 when it is missing or not a standard HTTP status. */
    private static int statusOf(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer status && HttpStatus.resolve(status) != null) {
            return status;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
 
    private static String requestedPath(HttpServletRequest request) {
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return uri != null ? uri.toString() : null;
    }
 
    /** Server errors are logged with their stack trace; the user only ever sees the friendly page. */
    private static void logServerError(HttpServletRequest request, int status) {
        if (status >= 500) {
            Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            log.error("Request to {} failed with status {}", requestedPath(request), status,
                    exception instanceof Throwable throwable ? throwable : null);
        }
    }
}
