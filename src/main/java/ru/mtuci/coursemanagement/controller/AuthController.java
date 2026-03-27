package ru.mtuci.coursemanagement.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.mtuci.coursemanagement.service.UserService;

import java.util.regex.Pattern;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,50}$");
    private final UserService users;

    @GetMapping("/login")
    public String loginPage(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        return "login";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           Model model) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            model.addAttribute("error", "Invalid username format");
            return "login";
        }
        if (password.length() < 8) {
            model.addAttribute("error", "Password must be at least 8 characters");
            return "login";
        }
        if (users.existsByUsername(username)) {
            model.addAttribute("error", "User already exists");
            return "login";
        }

        users.register(username, password);
        log.info("User {} registered", username);
        return "redirect:/login?registered";
    }
}
