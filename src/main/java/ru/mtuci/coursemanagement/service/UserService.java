package ru.mtuci.coursemanagement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.mtuci.coursemanagement.model.User;
import ru.mtuci.coursemanagement.repository.UserRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;

    public Optional<User> findByUsername(String u) {
        return repo.findByUsername(u);
    }

    public boolean existsByUsername(String username) {
        return repo.findByUsername(username).isPresent();
    }

    public User register(String username, String rawPassword) {
        User user = new User(null, username, passwordEncoder.encode(rawPassword), "STUDENT");
        return repo.save(user);
    }
}
