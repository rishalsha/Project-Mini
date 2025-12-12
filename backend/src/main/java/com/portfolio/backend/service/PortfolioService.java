package com.portfolio.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.backend.dto.ParseResponse;
import com.portfolio.backend.dto.PortfolioData;
import com.portfolio.backend.dto.ResumeAnalysis;
import com.portfolio.backend.entity.Portfolio;
import com.portfolio.backend.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final ObjectMapper objectMapper;
    private static final String UPLOAD_DIR = "uploads/resumes/";

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
        this.objectMapper = new ObjectMapper();
        // Create upload directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (Exception e) {
            System.err.println("Could not create upload directory: " + e.getMessage());
        }
    }

    public Portfolio savePortfolio(ParseResponse parseResponse, MultipartFile resumeFile) {
        try {
            Portfolio portfolio = new Portfolio();
            PortfolioData data = parseResponse.getPortfolio();
            ResumeAnalysis analysis = parseResponse.getAnalysis();

            // Map portfolio data
            portfolio.setFullName(data.getFullName());
            portfolio.setHeadline(data.getHeadline());
            portfolio.setAbout(data.getAbout());
            portfolio.setLocation(data.getLocation());
            portfolio.setEmail(data.getEmail());
            portfolio.setPhone(data.getPhone());
            portfolio.setLinkedin(data.getLinkedin());
            portfolio.setGithub(data.getGithub());
            portfolio.setWebsite(data.getWebsite());

            // Convert lists to JSON strings
            portfolio.setSkillsJson(objectMapper.writeValueAsString(data.getSkills()));
            portfolio.setExperienceJson(objectMapper.writeValueAsString(data.getExperience()));
            portfolio.setEducationJson(objectMapper.writeValueAsString(data.getEducation()));
            portfolio.setProjectsJson(objectMapper.writeValueAsString(data.getProjects()));

            // Map analysis data
            portfolio.setResumeScore(analysis.getScore());
            portfolio.setResumeSummary(analysis.getSummary());
            portfolio.setStrengthsJson(objectMapper.writeValueAsString(analysis.getStrengths()));
            portfolio.setWeaknessesJson(objectMapper.writeValueAsString(analysis.getWeaknesses()));
            portfolio.setMarketOutlook(analysis.getMarketOutlook());
            portfolio.setJobRecommendationsJson(objectMapper.writeValueAsString(analysis.getJobRecommendations()));

            // Save resume file if provided
            if (resumeFile != null && !resumeFile.isEmpty()) {
                String originalFilename = resumeFile.getOriginalFilename();
                String extension = originalFilename != null && originalFilename.contains(".")
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".pdf";
                String filename = UUID.randomUUID().toString() + extension;
                Path filePath = Paths.get(UPLOAD_DIR + filename);
                Files.copy(resumeFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                portfolio.setResumeFilePath(filename);
            }

            return portfolioRepository.save(portfolio);
        } catch (Exception e) {
            System.err.println("Error saving portfolio: " + e.getMessage());
            throw new RuntimeException("Failed to save portfolio", e);
        }
    }

    public Portfolio getPortfolioById(Long id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Portfolio not found with id: " + id));
    }

    public Portfolio getPortfolioByEmail(String email) {
        return portfolioRepository
                .findLatestNonEmptyByEmail(email)
                .or(() -> portfolioRepository.findFirstByEmailIgnoreCaseOrderByUpdatedAtDesc(email))
                .orElseThrow(() -> new RuntimeException("Portfolio not found with email: " + email));
    }
}
