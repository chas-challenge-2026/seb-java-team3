package se.comerit.seb.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Fired whenever @PreAuthorize denies an endpoint. REST clients get a plain 403; browser
 * (Thymeleaf) requests are sent back to the dashboard, mirroring the redirect the old
 * ad hoc role checks used to perform on denial.
 */
@Component
public class RoleAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Forbidden\"}");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/dashboard");
    }
}
