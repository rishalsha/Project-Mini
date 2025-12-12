package com.portfolio.backend.controller;

import com.portfolio.backend.entity.Portfolio;
import com.portfolio.backend.repository.PortfolioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:5174", "http://localhost:3000",
        "http://localhost:3001" })
public class PortfolioController {

    private final PortfolioRepository portfolioRepository;

    public PortfolioController(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @GetMapping
    public ResponseEntity<List<Portfolio>> getAllPortfolios() {
        List<Portfolio> portfolios = portfolioRepository.findAll();
        return ResponseEntity.ok(portfolios);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Portfolio>> searchPortfolios(@RequestParam("name") String name) {
        List<Portfolio> portfolios = portfolioRepository.findByFullNameContainingIgnoreCase(name);
        return ResponseEntity.ok(portfolios);
    }

    @GetMapping("/{email}")
    public ResponseEntity<Portfolio> getPortfolioByEmail(@PathVariable String email) {
        return portfolioRepository
                .findLatestNonEmptyByEmail(email)
                .or(() -> portfolioRepository.findFirstByEmailIgnoreCaseOrderByUpdatedAtDesc(email))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-email")
    public ResponseEntity<Portfolio> getPortfolioByEmailQuery(@RequestParam("email") String email) {
        return portfolioRepository
                .findLatestNonEmptyByEmail(email)
                .or(() -> portfolioRepository.findFirstByEmailIgnoreCaseOrderByUpdatedAtDesc(email))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/resume")
    public ResponseEntity<org.springframework.core.io.Resource> downloadResume(@PathVariable Long id) {
        try {
            Portfolio portfolio = portfolioRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Portfolio not found"));

            if (portfolio.getResumeFilePath() == null) {
                return ResponseEntity.notFound().build();
            }

            java.nio.file.Path filePath = java.nio.file.Paths.get("uploads/resumes/" + portfolio.getResumeFilePath());
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(
                    filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                String filename = portfolio.getFullName() != null
                        ? portfolio.getFullName().replace(" ", "_") + "_Resume.pdf"
                        : "Resume.pdf";

                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + filename + "\"")
                        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/pdf")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            System.err.println("Error downloading resume: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
