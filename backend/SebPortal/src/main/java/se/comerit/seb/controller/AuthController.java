package se.comerit.seb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.dto.UserResponse;
import se.comerit.seb.service.AuthService;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping({"/", "/login"})
    public String loginPage(HttpSession session) {
        if (session.getAttribute("userId") != null) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String email,
                          @RequestParam String password,
                          HttpSession session,
                          Model model) {
        Optional<User> user = authService.authenticate(email, password);
        if (user.isEmpty()) {
            model.addAttribute("error", "Invalid email or password.");
            return "login";
        }

        storeAuthenticatedUser(session, user.get());
        return "redirect:/dashboard";
    }

    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<?> apiLogin(@RequestBody LoginRequest request,
                                      HttpSession session) {
        Optional<User> user =
                authService.authenticate(request.getEmail(), request.getPassword());

        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        storeAuthenticatedUser(session, user.get());

        return ResponseEntity.ok(UserResponse.from(user.get()));
    }

    @GetMapping("/api/auth/me")
    @ResponseBody
    public ResponseEntity<?> currentUser(HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not logged in"));
        }

        try {
            return ResponseEntity.ok(userResponseFromSession(session));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not logged in"));
        }
    }

    private void storeAuthenticatedUser(HttpSession session, User user) {
        session.setAttribute("userId", user.getId());
        session.setAttribute("userName", user.getName());
        session.setAttribute("userEmail", user.getEmail());
        session.setAttribute("role", user.getRole().name());
        session.setAttribute("tenantId", user.getTenantId());
    }

    private UserResponse userResponseFromSession(HttpSession session) {
        return new UserResponse(
                (Long) session.getAttribute("userId"),
                (String) session.getAttribute("userName"),
                (String) session.getAttribute("userEmail"),
                resolveRole(session.getAttribute("role"))
        );
    }

    private Role resolveRole(Object value) {
        Role role = Role.fromSessionValue(value);

        if (role == null) {
            throw new IllegalStateException("Session role is missing or invalid");
        }

        return role;
    }

    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
