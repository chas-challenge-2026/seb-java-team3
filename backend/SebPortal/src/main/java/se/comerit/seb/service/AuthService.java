package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.UserRepository;

import java.security.MessageDigest;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> authenticate(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .filter(user -> md5Hash(rawPassword).equals(user.getPasswordHash()));
    }

    private static String md5Hash(String s) {
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
