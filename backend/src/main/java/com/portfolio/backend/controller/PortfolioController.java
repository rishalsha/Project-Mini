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
        return portfolioRepository.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
