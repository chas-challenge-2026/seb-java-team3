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

@Controller
public class AuthController {

    // TODO: this should be in a service/repository layer, but inline works for now
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        String md5 = md5Hash(password); // BUG-002: MD5 is cryptographically broken

        // BUG-001: SQL injection — string concatenation instead of PreparedStatement
        // TODO: use parameterized query
        String sql = "SELECT id, name, email, role, tenant_id FROM users WHERE email = '"
                + email + "' AND password_md5 = '" + md5 + "'";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        if (rows.isEmpty()) {
            model.addAttribute("error", "Fel e-post eller lösenord.");
            return "login";
        }
        Map<String, Object> u = rows.get(0);
        session.setAttribute("userId", u.get("id"));
        session.setAttribute("userName", u.get("name"));
        session.setAttribute("userEmail", u.get("email"));
        session.setAttribute("role", u.get("role"));
        session.setAttribute("tenantId", u.get("tenant_id"));
        return "redirect:/dashboard";
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
