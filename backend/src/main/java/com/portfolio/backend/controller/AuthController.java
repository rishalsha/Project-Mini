package com.portfolio.backend.controller;

import com.portfolio.backend.entity.User;
import com.portfolio.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = { "http://localhost:3210", "http://localhost:5173", "http://localhost:5174",
        "http://localhost:3000",
        "http://localhost:3001" })
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "");
        String email = body.getOrDefault("email", "");
        String password = body.getOrDefault("password", "");
        String resumeFilePath = body.getOrDefault("resumeFilePath", null);
        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            User user = userService.register(name, email, password, resumeFilePath);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("unique constraint") ||
                    e.getMessage() != null && e.getMessage().contains("already exists")) {
                return ResponseEntity.status(409).body(Map.of("error", "Email already registered"));
            }
            return ResponseEntity.status(500).body(Map.of("error", "Registration failed"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "");
        String password = body.getOrDefault("password", "");
        if (email.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<User> user = userService.login(email, password);
        return user.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @GetMapping("/user")
    public ResponseEntity<User> getUserByEmail(@RequestParam("email") String email) {
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return userService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        if (email == null || password == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean updated = userService.updatePassword(email, password);
        if (updated) {
            return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
        } else {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
    }
}
