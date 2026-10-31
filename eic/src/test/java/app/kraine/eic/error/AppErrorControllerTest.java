package app.kraine.eic.error;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import app.kraine.eic.controller.AppErrorController;

import static org.junit.jupiter.api.Assertions.*;

class AppErrorControllerTest {

    private final AppErrorController controller = new AppErrorController();

    private static MockHttpServletRequest request(Object status, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (status != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        }
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, uri);
        return request;
    }

    @Test
    void showsTheErrorPageWithTheStatusAndPath() {
        Model model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.errorPage(request(404, "/nowhere"), response, model);

        assertEquals("error", view);
        assertEquals(404, response.getStatus());
        assertEquals(404, model.getAttribute("status"));
        assertEquals("Not Found", model.getAttribute("error"));
        assertEquals("/nowhere", model.getAttribute("path"));
        assertNotNull(model.getAttribute("timestamp"));
    }

    @Test
    void treatsAMissingOrNonStandardStatusAsAServerError() {
        for (Object status : new Object[]{null, 999, "404"}) {
            Model model = new ConcurrentModel();
            MockHttpServletResponse response = new MockHttpServletResponse();

            controller.errorPage(request(status, "/x"), response, model);

            assertEquals(500, response.getStatus(), String.valueOf(status));
            assertEquals(500, model.getAttribute("status"));
        }
    }

    @Test
    void jsonKeepsTheStatusAndShowsTheReasonForClientErrors() {
        ResponseEntity<ProblemDetail> response = controller.errorJson(request(403, "/users"));

        assertEquals(403, response.getStatusCode().value());
        assertEquals("Forbidden", response.getBody().getDetail());
    }

    @Test
    void jsonNeverExposesInternalDetailsForServerErrors() {
        MockHttpServletRequest request = request(500, "/users");
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "secret database password");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("secret database password"));

        ResponseEntity<ProblemDetail> response = controller.errorJson(request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("An unexpected error occurred.", response.getBody().getDetail());
        assertFalse(response.getBody().toString().contains("secret"));
    }
}
