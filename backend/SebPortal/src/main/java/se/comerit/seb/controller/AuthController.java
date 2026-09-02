package se.comerit.seb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.SecureRandom;
import java.util.Base64;

@Controller
public class AuthController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // TODO: this should be in a service/repository layer, but inline works for now
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // BUG-010: hardcoded connection string duplicated across controllers
    // TODO: read from config, not a constant in every file
    static final String JDBC_FALLBACK =
        "Host=localhost;Port=5432;Database=seb;Username=seb;Password=seb123";

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
        List<Map<String, Object>> rows = findUser(email, password);
        if (rows.isEmpty()) {
            model.addAttribute("error", "Invalid email or password.");
            return "login";
        }

        storeAuthenticatedUser(session, rows.get(0));
        return "redirect:/dashboard";
    }

    @PostMapping("/api/login")
    @ResponseBody
    public ResponseEntity<?> apiLogin(@RequestBody LoginRequest request,
                                      HttpSession session) {
        List<Map<String, Object>> users =
                findUser(request.getEmail(), request.getPassword());

        if (users.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        storeAuthenticatedUser(session, users.get(0));

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);

        session.setAttribute("authToken", token);

        return ResponseEntity.ok(Map.of("token", token));
    }

    private List<Map<String, Object>> findUser(String email, String password) {
        String sql =
                "SELECT id, name, email, role, tenant_id " +
                "FROM users " +
                "WHERE email = ? AND password_md5 = ?";

        return jdbcTemplate.queryForList(sql, email, md5Hash(password));
    }

    private void storeAuthenticatedUser(HttpSession session,
                                        Map<String, Object> user) {
        session.setAttribute("userId", user.get("id"));
        session.setAttribute("userName", user.get("name"));
        session.setAttribute("userEmail", user.get("email"));
        session.setAttribute("role", user.get("role"));
        session.setAttribute("tenantId", user.get("tenant_id"));
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

    // TODO: upgrade to bcrypt/argon2 — copy in every controller (BUG-002/010)
    static String md5Hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
