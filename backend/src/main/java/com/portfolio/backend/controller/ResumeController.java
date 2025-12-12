package com.portfolio.backend.controller;

import com.portfolio.backend.dto.ParseResponse;
import com.portfolio.backend.dto.PortfolioData;
import com.portfolio.backend.dto.ResumeAnalysis;
import com.portfolio.backend.entity.Portfolio;
import com.portfolio.backend.entity.ResumeAnalysisEntity;
import com.portfolio.backend.entity.User;
import com.portfolio.backend.service.DocumentParserService;
import com.portfolio.backend.service.OllamaService;
import com.portfolio.backend.service.PortfolioService;
import com.portfolio.backend.repository.ResumeAnalysisRepository;
import com.portfolio.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:5174", "http://localhost:3000",
        "http://localhost:3001" })
public class ResumeController {

    private final DocumentParserService documentParserService;
    private final OllamaService ollamaService;
    private final PortfolioService portfolioService;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final UserRepository userRepository;

    public ResumeController(DocumentParserService documentParserService,
            OllamaService ollamaService,
            PortfolioService portfolioService,
            ResumeAnalysisRepository resumeAnalysisRepository,
            UserRepository userRepository) {
        this.documentParserService = documentParserService;
        this.ollamaService = ollamaService;
        this.portfolioService = portfolioService;
        this.resumeAnalysisRepository = resumeAnalysisRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/parse")
    public ResponseEntity<ParseResponse> parseResume(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text) {

        try {
            String resumeText;

            if (file != null && !file.isEmpty()) {
                System.out.println("Processing file: " + file.getOriginalFilename());
                resumeText = documentParserService.extractText(file);
            } else if (text != null && !text.isEmpty()) {
                System.out.println("Processing text input");
                resumeText = text;
            } else {
                return ResponseEntity.badRequest().build();
            }

            System.out.println("Parsing resume with Ollama...");
            PortfolioData portfolio = ollamaService.parseResume(resumeText);

            System.out.println("Analyzing resume with Ollama...");
            ResumeAnalysis analysis = ollamaService.analyzeResume(resumeText);

            ParseResponse response = new ParseResponse();
            response.setPortfolio(portfolio);
            response.setAnalysis(analysis);

            // Save to PostgreSQL database
            System.out.println("Saving portfolio to database...");
            Portfolio savedPortfolio = portfolioService.savePortfolio(response);
            System.out.println("Portfolio saved with ID: " + savedPortfolio.getId());

            // Persist analysis per user (if available)
            userRepository.findByEmail(portfolio.getEmail()).ifPresent(user -> {
                ResumeAnalysisEntity ra = new ResumeAnalysisEntity();
                ra.setUser(user);
                ra.setResumeText(resumeText);
                ra.setAnalysisScores("{\"overall\":" + (analysis.getScore() == null ? 0 : analysis.getScore()) + "}");
                ra.setStrengths(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .valueToTree(analysis.getStrengths()).toString());
                ra.setWeaknesses(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .valueToTree(analysis.getWeaknesses()).toString());
                ra.setIdentifiedSkills(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .valueToTree(portfolio.getSkills()).toString());
                ra.setRecommendedSkills("[]");
                resumeAnalysisRepository.save(ra);
            });

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error processing resume: " + e.getMessage());
            // Return a clearer error message to the client
            return ResponseEntity.status(500).body(new ParseResponse());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Resume API is running");
    }
}
