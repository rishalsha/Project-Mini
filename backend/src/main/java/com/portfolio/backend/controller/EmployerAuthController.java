package com.portfolio.backend.controller;

import com.portfolio.backend.entity.Employer;
import com.portfolio.backend.service.EmployerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/employer/auth")
@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:5174", "http://localhost:3000",
        "http://localhost:3001" })
public class EmployerAuthController {

    private final EmployerService employerService;

    public EmployerAuthController(EmployerService employerService) {
        this.employerService = employerService;
    }

    @PostMapping("/register")
    public ResponseEntity<Employer> register(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "");
        String email = body.getOrDefault("email", "");
        String password = body.getOrDefault("password", "");
        String companyName = body.getOrDefault("companyName", "");
        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Employer employer = employerService.register(name, email, password, companyName);
        return ResponseEntity.ok(employer);
    }

    @PostMapping("/login")
    public ResponseEntity<Employer> login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "");
        String password = body.getOrDefault("password", "");
        if (email.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Employer> employer = employerService.login(email, password);
        return employer.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @GetMapping("/employer")
    public ResponseEntity<Employer> getEmployerByEmail(@RequestParam("email") String email) {
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return employerService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}