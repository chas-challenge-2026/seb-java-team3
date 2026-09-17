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

import se.comerit.seb.domain.User;
import se.comerit.seb.dto.LoginResponse;
import se.comerit.seb.dto.UserResponse;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.AuthService;

@Controller
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final JwtUserContext jwtUserContext;
    private final UserRepository userRepository;

    public AuthController(AuthService authService,
                          JwtService jwtService,
                          JwtUserContext jwtUserContext,
                          UserRepository userRepository) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.jwtUserContext = jwtUserContext;
        this.userRepository = userRepository;
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

        String token = jwtService.generateToken(toAuthenticatedUserContext(user.get()));
        return ResponseEntity.ok(LoginResponse.from(user.get(), token));
    }

    @GetMapping("/api/auth/me")
    @ResponseBody
    public ResponseEntity<?> currentUser() {
        AuthenticatedUserContext authenticated = jwtUserContext.requireAuthenticated();
        User user = userRepository.findById(authenticated.userId())
                .orElseThrow(() -> new IllegalStateException("User in token not found: " + authenticated.userId()));

        return ResponseEntity.ok(UserResponse.from(user));
    }

    private AuthenticatedUserContext toAuthenticatedUserContext(User user) {
        return new AuthenticatedUserContext(user.getId(), user.getTenantId(), user.getRole());
    }

    private void storeAuthenticatedUser(HttpSession session, User user) {
        session.setAttribute("userId", user.getId());
        session.setAttribute("userName", user.getName());
        session.setAttribute("userEmail", user.getEmail());
        session.setAttribute("role", user.getRole());
        session.setAttribute("tenantId", user.getTenantId());
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
