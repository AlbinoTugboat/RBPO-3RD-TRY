package ru.mtuci.coursemanagement.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.mtuci.coursemanagement.model.User;
import ru.mtuci.coursemanagement.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createUserIfMissing("teacher", "password", "TEACHER");
        createUserIfMissing("student", "password", "STUDENT");
    }

    private void createUserIfMissing(String username, String rawPassword, String role) {
        userRepository.findByUsername(username).orElseGet(() ->
                userRepository.save(new User(null, username, passwordEncoder.encode(rawPassword), role)));
    }
}
