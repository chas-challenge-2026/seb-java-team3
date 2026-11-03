package se.comerit.seb.exception;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.comerit.seb.service.NoAttestantAvailableException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// R-18: felsvar ska inte lämna ut interna id:n, undantagstexter eller stack traces till klienten.
// Fel som Spring och Spring Security ska hantera måste samtidigt behålla sin status.
class GlobalExceptionHandlerTest {

    private static final String INTERNAL_DETAIL = "Account balance is missing: 4711";

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/test/internal-error")
        void internalError() {
            throw new IllegalStateException(INTERNAL_DETAIL);
        }

        @GetMapping("/test/checked-error")
        void checkedError() throws IOException {
            throw new IOException(INTERNAL_DETAIL);
        }

        @GetMapping("/test/no-attestant")
        void noAttestant() {
            throw new NoAttestantAvailableException(4711L);
        }

        @GetMapping("/test/not-your-step")
        void notYourStep() {
            throw new ApprovalStepAccessDeniedException("Approval step 4711 is not assigned to actor 4712");
        }

        @GetMapping("/test/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("Access Denied");
        }

        @GetMapping("/test/not-authenticated")
        void notAuthenticated() {
            throw new AuthenticationCredentialsNotFoundException("No authentication");
        }

        @GetMapping("/test/status")
        void responseStatus() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }

        @GetMapping("/test/number/{id}")
        void number(@PathVariable Long id) {
        }

        @PostMapping("/test/body")
        void body(@RequestBody Map<String, String> body) {
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unexpectedRuntimeException_returns500WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/test/internal-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.UNEXPECTED_ERROR_MESSAGE))
                .andExpect(content().string(not(containsString("4711"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void unexpectedCheckedException_returns500WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/test/checked-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.UNEXPECTED_ERROR_MESSAGE))
                .andExpect(content().string(not(containsString("4711"))));
    }

    @Test
    void noAttestant_returns409WithoutTheTenantId() throws Exception {
        mockMvc.perform(get("/test/no-attestant"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.NO_ATTESTANT_MESSAGE))
                .andExpect(content().string(not(containsString("4711"))));
    }

    @Test
    void approvalStepAccessDenied_returns403WithoutStepOrUserId() throws Exception {
        mockMvc.perform(get("/test/not-your-step"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.ACCESS_DENIED_MESSAGE))
                .andExpect(content().string(not(containsString("4711"))))
                .andExpect(content().string(not(containsString("4712"))));
    }

    // Kastas vidare till Spring Securitys filter, som ger 403 och 401. Hela kedjan testas i RoleAuthorizationTest.
    @Test
    void accessDenied_isPassedOnToSpringSecurity() {
        ServletException thrown = assertThrows(ServletException.class,
                () -> mockMvc.perform(get("/test/access-denied")));

        assertInstanceOf(AccessDeniedException.class, thrown.getCause());
    }

    @Test
    void missingAuthentication_isPassedOnToSpringSecurity() {
        ServletException thrown = assertThrows(ServletException.class,
                () -> mockMvc.perform(get("/test/not-authenticated")));

        assertInstanceOf(AuthenticationCredentialsNotFoundException.class, thrown.getCause());
    }

    @Test
    void responseStatusException_keepsItsStatus() throws Exception {
        mockMvc.perform(get("/test/status"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongTypeInPath_stays400() throws Exception {
        mockMvc.perform(get("/test/number/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void brokenJsonBody_stays400() throws Exception {
        mockMvc.perform(post("/test/body").contentType(APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongHttpMethod_stays405() throws Exception {
        mockMvc.perform(post("/test/internal-error"))
                .andExpect(status().isMethodNotAllowed());
    }

    // Springs egna /error-svar används när ett fel inte når den här klassen, till exempel utanför en controller.
    // Testet läser den riktiga konfigurationen, så att ett oavsiktligt ändrat värde märks.
    @Test
    void springsOwnErrorResponse_neverIncludesDetails() throws Exception {
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(Path.of("src/main/resources/application.properties"))));

        assertEquals("never", properties.getProperty("server.error.include-stacktrace"));
        assertEquals("never", properties.getProperty("server.error.include-message"));
        assertEquals("never", properties.getProperty("server.error.include-binding-errors"));
        assertEquals("false", properties.getProperty("server.error.include-exception"));
    }
}
