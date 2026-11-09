package app.kraine.eic.equipmentManagement;

import app.kraine.eic.service.EquipmentImageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Spring rejects an oversized upload (spring.servlet.multipart.max-file-size) before any controller
 * runs, so it is handled here for both the Thymeleaf form and the JSON API.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class EquipmentImageExceptionHandler {

    private static final Pattern FORM_UPLOAD_PATH = Pattern.compile("^/equipment/\\d+/image$");
    private static final Pattern API_UPLOAD_PATH = Pattern.compile("^/api/equipment/\\d+/image$");

    private final EquipmentImageService imageService;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleTooLarge(MaxUploadSizeExceededException ex,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirect) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String message = "The selected file is too large. " + imageService.limitsDescription();

        if (API_UPLOAD_PATH.matcher(path).matches()) {
            // Same shape as the ProblemDetail bodies returned by EquipmentImageApiExceptionHandler.
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"status\":413,\"title\":\"Payload Too Large\",\"detail\":\""
                    + escapeJson(message) + "\"}");
            return null; // the response is already written
        }
        if (!FORM_UPLOAD_PATH.matcher(path).matches()) {
            throw ex; // not one of our endpoints: leave it to the default handling
        }
        redirect.addFlashAttribute("errorMessage", message);
        return "redirect:" + path + "/upload";
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
