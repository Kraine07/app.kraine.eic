package app.kraine.eic.setup;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import app.kraine.eic.service.FirstRunService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * UA-20: while no user exists, nothing but the setup form is available. Pages are redirected to
 * /setup; API calls get a 409 explaining that setup is needed. Runs before Spring Security (whose
 * filter order is -100), otherwise anonymous visitors would be sent to /login first.
 */
@Component
@Order(-101)
@RequiredArgsConstructor
public class FirstRunSetupFilter extends OncePerRequestFilter {

    /** Reachable during setup. Add the static folders your pages use. */
    private static final Set<String> ALLOWED_PATHS = Set.of("/setup", "/favicon.ico", "/error");
    private static final List<String> ALLOWED_PREFIXES =
            List.of("/setup/", "/api/setup/", "/css/", "/js/", "/images/", "/webjars/");

    private final FirstRunService firstRunService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        // CORS preflight requests carry no credentials and must not be blocked.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !firstRunService.isSetupRequired() || isAllowed(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"status\":409,\"title\":\"Setup required\","
                    + "\"detail\":\"Initial setup has not been completed. Create the first administrator account.\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/setup");
        }
    }

    private static boolean isAllowed(String path) {
        return ALLOWED_PATHS.contains(path) || ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
