package app.kraine.eic.setup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import app.kraine.eic.service.FirstRunService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirstRunSetupFilterTest {

    @Mock private FirstRunService firstRunService;

    private FirstRunSetupFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new FirstRunSetupFilter(firstRunService);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    private void request(String method, String path) throws Exception {
        filter.doFilter(new MockHttpServletRequest(method, path), response, chain);
    }

    @Test
    void redirectsPagesToTheSetupFormWhileSetupIsRequired() throws Exception {
        when(firstRunService.isSetupRequired()).thenReturn(true);

        request("GET", "/login");

        assertEquals("/setup", response.getRedirectedUrl());
        assertNull(chain.getRequest(), "the request must not reach the application");
    }

    @Test
    void answersApiCallsWith409WhileSetupIsRequired() throws Exception {
        when(firstRunService.isSetupRequired()).thenReturn(true);

        request("GET", "/api/equipment");

        assertEquals(409, response.getStatus());
        assertTrue(response.getContentAsString().contains("Initial setup has not been completed"));
        assertNull(chain.getRequest());
    }

    @Test
    void letsTheSetupFormItsApiAndStaticFilesThrough() throws Exception {
        when(firstRunService.isSetupRequired()).thenReturn(true);

        for (String path : new String[]{"/setup", "/api/setup/status", "/api/setup/administrator", "/css/app.css"}) {
            chain = new MockFilterChain();
            request(path.startsWith("/api/setup/administrator") ? "POST" : "GET", path);
            assertNotNull(chain.getRequest(), path);
        }
    }

    @Test
    void doesNotBlockCorsPreflightRequests() throws Exception {
        request("OPTIONS", "/api/equipment");

        assertNotNull(chain.getRequest());
    }

    @Test
    void doesNothingOnceUsersExist() throws Exception {
        when(firstRunService.isSetupRequired()).thenReturn(false);

        request("GET", "/equipment");

        assertNotNull(chain.getRequest());
        assertNull(response.getRedirectedUrl());
    }
}
