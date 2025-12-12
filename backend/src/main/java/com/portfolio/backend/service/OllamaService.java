package com.portfolio.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.backend.dto.PortfolioData;
import com.portfolio.backend.dto.ResumeAnalysis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OllamaService {

    @Value("${ollama.url}")
    private String ollamaUrl;

    @Value("${ollama.model}")
    private String ollamaModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PortfolioData parseResume(String resumeText) {
        String prompt = String.format(
                """
                        You are an expert resume parser. Extract data from the provided resume and return ONLY valid JSON matching this structure (no markdown, no extra text):

                        {
                          "fullName": "string",
                          "headline": "string (job title/role)",
                          "about": "string (compelling professional bio)",
                          "location": "string",
                          "email": "string",
                          "phone": "string",
                          "linkedin": "string (URL)",
                          "github": "string (URL)",
                          "website": "string (URL)",
                          "skills": [
                            {
                              "name": "string",
                              "level": number (0-100),
                              "category": "frontend|backend|design|soft-skills|tools|other"
                            }
                          ],
                          "experience": [
                            {
                              "company": "string",
                              "role": "string",
                              "period": "string (e.g. Jan 2020 - Present)",
                              "description": "string"
                            }
                          ],
                          "education": [
                            {
                              "institution": "string",
                              "degree": "string",
                              "year": "string"
                            }
                          ],
                          "projects": [
                            {
                              "name": "string",
                              "description": "string",
                              "technologies": ["string"],
                              "link": "string (URL)"
                            }
                          ]
                        }

                        Resume content:
                        %s

                        Return ONLY the JSON object, nothing else.
                        """,
                resumeText);

        try {
            String response = callOllama(prompt);
            return parseJsonResponse(response, PortfolioData.class);
        } catch (Exception e) {
            System.err.println("Error parsing resume: " + e.getMessage());
            e.printStackTrace();
            // Graceful fallback: return a minimal PortfolioData derived from plain text
            PortfolioData fallback = new PortfolioData();
            // Try to infer email and name rudimentarily
            String email = inferEmail(resumeText);
            String name = inferName(resumeText);
            fallback.setEmail(email);
            fallback.setFullName(name != null ? name : "Unknown");
            fallback.setHeadline("Resume");
            fallback.setAbout(resumeText.length() > 400 ? resumeText.substring(0, 400) + "..." : resumeText);
            fallback.setLocation("");
            fallback.setPhone("");
            fallback.setLinkedin("");
            fallback.setGithub("");
            fallback.setWebsite("");
            fallback.setSkills(new java.util.ArrayList<>());
            fallback.setExperience(new java.util.ArrayList<>());
            fallback.setEducation(new java.util.ArrayList<>());
            fallback.setProjects(new java.util.ArrayList<>());
            return fallback;
        }
    }

    public ResumeAnalysis analyzeResume(String resumeText) {
        String prompt = String.format("""
                Analyze this candidate's resume and provide career insights. Return ONLY valid JSON (no markdown):

                {
                  "score": number (0-100 based on clarity, impact, completeness),
                  "summary": "string (overall critique)",
                  "strengths": ["string"],
                  "weaknesses": ["string"],
                  "marketOutlook": "string (current demand for these skills)",
                  "jobRecommendations": [
                    {
                      "title": "string",
                      "company": "string",
                      "location": "string",
                      "matchReason": "string"
                    }
                  ]
                }

                Resume content:
                %s

                Return ONLY the JSON object.
                """, resumeText);

        try {
            String response = callOllama(prompt);
            return parseJsonResponse(response, ResumeAnalysis.class);
        } catch (Exception e) {
            System.err.println("Error analyzing resume: " + e.getMessage());
            e.printStackTrace();
            // Graceful fallback: minimal analysis
            ResumeAnalysis fallback = new ResumeAnalysis();
            fallback.setScore(50);
            fallback.setSummary("Automated fallback: Unable to analyze via model; showing basic summary.");
            java.util.List<String> strengths = new java.util.ArrayList<>();
            strengths.add("Provided resume text parsed successfully.");
            fallback.setStrengths(strengths);
            java.util.List<String> weaknesses = new java.util.ArrayList<>();
            weaknesses.add("AI analysis failed; results limited.");
            fallback.setWeaknesses(weaknesses);
            fallback.setMarketOutlook("N/A");
            fallback.setJobRecommendations(new java.util.ArrayList<>());
            return fallback;
        }
    }

    private String callOllama(String prompt) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("model", ollamaModel);
        request.put("prompt", prompt);
        request.put("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                ollamaUrl,
                HttpMethod.POST,
                entity,
                String.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.get("response").asText();
        }

        throw new Exception("Failed to get response from Ollama");
    }

    private <T> T parseJsonResponse(String text, Class<T> clazz) throws Exception {
        // Clean up markdown formatting
        text = text.replaceAll("```json", "").replaceAll("```", "").trim();

        // Find JSON object in the response
        Pattern pattern = Pattern.compile("\\{[\\s\\S]*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String jsonText = matcher.group();
            return objectMapper.readValue(jsonText, clazz);
        }

        throw new Exception("No valid JSON found in response");
    }

    // Very lightweight heuristics to infer an email and name from raw text
    private String inferEmail(String text) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text);
            if (m.find())
                return m.group();
        } catch (Exception ignored) {
        }
        return null;
    }

    private String inferName(String text) {
        try {
            // Assume first non-empty line could be a name (simple heuristic)
            String[] lines = text.split("\r?\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.length() >= 3 && trimmed.length() <= 80 && !trimmed.contains("@")
                        && !trimmed.matches(".*\\d.*")) {
                    return trimmed;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
