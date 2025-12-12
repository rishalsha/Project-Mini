package com.portfolio.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.backend.dto.ParseResponse;
import com.portfolio.backend.dto.PortfolioData;
import com.portfolio.backend.dto.ResumeAnalysis;
import com.portfolio.backend.entity.Portfolio;
import com.portfolio.backend.repository.PortfolioRepository;
import org.springframework.stereotype.Service;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final ObjectMapper objectMapper;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
        this.objectMapper = new ObjectMapper();
    }

    public Portfolio savePortfolio(ParseResponse parseResponse) {
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
        return portfolioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Portfolio not found with email: " + email));
    }
}
