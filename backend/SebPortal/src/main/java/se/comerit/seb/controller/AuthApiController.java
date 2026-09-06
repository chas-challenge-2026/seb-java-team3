package se.comerit.seb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpSession session) {
        String sql = "SELECT id, name, email, role, tenant_id FROM users "
                + "WHERE email = ? AND password_md5 = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql, request.email(), AuthController.md5Hash(request.password()));

        if (rows.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "Fel e-post eller lösenord."));
        }

        Map<String, Object> user = rows.get(0);
        session.setAttribute("userId", user.get("id"));
        session.setAttribute("userName", user.get("name"));
        session.setAttribute("userEmail", user.get("email"));
        session.setAttribute("role", user.get("role"));
        session.setAttribute("tenantId", user.get("tenant_id"));

        return ResponseEntity.ok(Map.of(
                "name", user.get("name"),
                "email", user.get("email"),
                "role", user.get("role")));
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Inte inloggad."));
        }

        return ResponseEntity.ok(Map.of(
                "name", session.getAttribute("userName"),
                "email", session.getAttribute("userEmail"),
                "role", session.getAttribute("role")));
    }

    public static class LoginRequest {
        private String email;
        private String password;

        public String email() {
            return email;
        }

        public String password() {
            return password;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}