package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.UserRepository;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> authenticate(String email, String rawPassword) {
        String normalizedEmail = EmailAddress.normalize(email);
        // En tom adress ska aldrig slås upp: Spring Data översätter null till "email IS NULL" och skulle
        // då kunna hitta en användare som saknar e-post.
        if (normalizedEmail == null || normalizedEmail.isEmpty()) {
            return Optional.empty();
        }

        return userRepository.findByEmail(normalizedEmail)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()));
    }
}
