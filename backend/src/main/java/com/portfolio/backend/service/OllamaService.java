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
            You are an expert resume parser. Extract data from the provided resume and return ONLY valid JSON matching this structure.

            CRITICAL RULES:
            1. Return ONLY the JSON object - no markdown formatting (```json), no explanations, no extra text
            2. Keep personal names exactly as written in the resume - NEVER use addresses, companies, or locations as names
            3. ALL fields are REQUIRED - use appropriate placeholders when information is missing
            4. Follow the exact field order shown below
            5. Be consistent - same resume should produce same output every time
            6. Do not fabricate information - use "Not specified" or empty arrays when data is unavailable

            REQUIRED JSON STRUCTURE (return fields in this exact order):
            {
              "fullName": "Extract the person's full name ONLY (not address, not company). If not found: 'Name Not Provided'",
              "headline": "Extract current job title or desired role. If not found: 'Professional'",
              "about": "Create a compelling 2-3 sentence professional summary based on experience and skills. If minimal info: 'Seeking opportunities to leverage skills and experience'",
              "location": "Extract city, state/country. If not found: 'Location Not Specified'",
              "email": "Extract email address. If not found: 'email@notprovided.com'",
              "phone": "Extract phone number with formatting. If not found: 'Not Provided'",
              "linkedin": "Extract LinkedIn URL (must start with http:// or https://). If not found: ''",
              "github": "Extract GitHub URL (must start with http:// or https://). If not found: ''",
              "website": "Extract personal website/portfolio URL. If not found: ''",
              "skills": [
                {
                  "name": "Skill name from resume",
                  "level": 70-90 for experienced skills, 50-70 for intermediate, 30-50 for beginner (number between 0-100),
                  "category": "Must be one of: 'frontend', 'backend', 'design', 'soft-skills', 'tools', 'other'"
                }
              ],
              "experience": [
                {
                  "company": "Company/Organization name. If not available: 'Company Not Specified'",
                  "role": "Job title/position. If not available: 'Role Not Specified'",
                  "period": "Date range (e.g., 'Jan 2020 - Present' or 'Jun 2019 - Dec 2021'). If not available: 'Dates Not Specified'",
                  "description": "Detailed description of responsibilities and achievements (2-4 bullet points worth). If minimal info: 'Responsible for various duties and tasks'"
                }
              ],
              "education": [
                {
                  "institution": "School/University name. If not available: 'Institution Not Specified'",
                  "degree": "Degree type and major (e.g., 'Bachelor of Science in Computer Science'). If not available: 'Degree Not Specified'",
                  "year": "Graduation year or date range. If not available: 'Year Not Specified'"
                }
              ],
              "projects": [
                {
                  "name": "Project name. If not available: 'Project Not Specified'",
                  "description": "Detailed project description including purpose and impact. If minimal info: 'Project details not provided'",
                  "technologies": ["Array of technologies used - must be an array even if empty"],
                  "link": "Project URL if available, otherwise empty string ''"
                }
              ]
            }

            IMPORTANT NOTES:
            - If experience section is empty, return empty array []
            - If education section is empty, return at least one entry with placeholder text
            - If projects section is empty, return empty array []
            - For projects, include EVERY distinct project mentioned in the resume (do not merge or drop them). If 3-5 projects are present, return all of them. If names repeat, keep separate entries when descriptions differ.
            - If skills are mentioned but not explicitly listed, infer from experience and projects
            - Ensure skills array has at least 3-5 skills if any are mentioned in resume
            - For missing or unclear information, use the specified placeholder text consistently
            - Always return valid JSON with all fields present

            Resume content:
            %s

            Remember: Return ONLY the JSON object with all required fields, in the exact order specified above. No markdown, no explanations.
            """,
        resumeText);

    try {
      String response = callOllama(prompt);
      PortfolioData parsed = parseJsonResponse(response, PortfolioData.class);
      parsed.setFullName(sanitizeFullName(parsed.getFullName(), resumeText));
      return parsed;
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
    String prompt = String.format(
        """
            You are an expert career advisor and resume analyst. Analyze this candidate's resume and provide detailed career insights.

            CRITICAL RULES:
            1. Return ONLY valid JSON - no markdown formatting (```json), no explanations, no extra text
            2. ALL fields are REQUIRED and MUST NEVER be empty
            3. Be consistent - same resume should produce same analysis every time
            4. Follow the exact field order shown below
            5. Provide constructive, actionable feedback in all sections

            REQUIRED JSON STRUCTURE (return fields in this exact order):
            {
              "score": Calculate an objective score (0-100) based on:
                - Resume clarity and formatting (20 points)
                - Quantifiable achievements and impact (25 points)
                - Skills relevance and depth (25 points)
                - Experience quality and progression (20 points)
                - Overall completeness (10 points)
                Return a number between 40-95 (most resumes score 60-80),

              "summary": "Provide a comprehensive 3-4 sentence overall critique covering:
                - Overall impression of the candidate's qualifications
                - Career level assessment (entry/mid/senior)
                - Key differentiators or standout qualities
                - General recommendation for improvement focus
                MUST NOT BE EMPTY - always provide thoughtful analysis",

              "strengths": [
                "List 3-5 specific strengths found in the resume",
                "Focus on: strong skills, relevant experience, notable achievements, good presentation",
                "Be specific with examples from the resume",
                "If resume is weak, mention: 'Clear contact information', 'Professional formatting', 'Basic structure present'",
                "MUST contain at least 3 items - NEVER return empty array"
              ],

              "weaknesses": [
                "List 3-5 specific areas for improvement (this field is CRITICAL - MUST NEVER be empty)",
                "Focus on: missing information, unclear descriptions, lack of quantifiable results, skill gaps",
                "Provide actionable feedback like: 'Add quantifiable metrics to achievements', 'Include more technical skills', 'Expand project descriptions'",
                "Common issues: 'Limited work experience details', 'No measurable accomplishments mentioned', 'Missing relevant certifications', 'Could benefit from more specific technical skills', 'Project outcomes not clearly stated'",
                "If resume is strong, suggest: 'Consider adding metrics to demonstrate impact', 'Could include more leadership examples', 'Add more recent projects'",
                "MUST contain at least 3-5 items - this is MANDATORY and MUST NEVER be empty or null"
              ],

              "marketOutlook": "Provide 2-3 sentences about current market demand for this candidate's skills and experience:
                - Industry trends relevant to their skills
                - Job market competitiveness for their profile
                - Salary expectations or growth potential
                - Recommendations for skills to develop
                MUST NOT BE EMPTY - always provide market context like: 'The skills demonstrated show relevance in current market. Consider strengthening expertise in emerging technologies for better opportunities.'",

              "jobRecommendations": [
                {
                  "title": "Specific job title matching their skills (e.g., 'Senior Java Developer', 'Full Stack Engineer')",
                  "company": "Type of company (e.g., 'Technology Startups', 'Fortune 500 Companies', 'Consulting Firms')",
                  "location": "Recommended job locations (e.g., 'Remote', 'San Francisco, CA', 'Major Tech Hubs')",
                  "matchReason": "Explain why this role fits (2-3 sentences) based on their skills and experience"
                }
              ]
              Note: Provide 2-4 job recommendations. If skills are unclear, suggest: 'Entry Level Positions', 'Junior Developer Roles', 'Internship Programs'
            }

            VALIDATION CHECKLIST - Ensure before returning:
            ✓ score is a number between 40-95
            ✓ summary is 3-4 sentences and not empty
            ✓ strengths array has 3-5 items and is NEVER empty
            ✓ weaknesses array has 3-5 items and is NEVER EVER empty (CRITICAL)
            ✓ marketOutlook is 2-3 sentences and not empty
            ✓ jobRecommendations has 2-4 entries with all fields filled
            ✓ All fields are present in the exact order specified
            ✓ No null or empty values for any required fields

            Resume content:
            %s

            Remember: Return ONLY the JSON object with ALL required fields properly filled. The weaknesses array is MANDATORY and must contain 3-5 actionable items. No markdown, no explanations.
            """,
        resumeText);

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

    // Add options for more consistent and deterministic responses
    Map<String, Object> options = new HashMap<>();
    options.put("temperature", 0.3); // Lower temperature for more consistent outputs (default is 0.8)
    options.put("top_p", 0.9); // Nucleus sampling for better quality
    options.put("top_k", 40); // Limit token selection for consistency
    options.put("repeat_penalty", 1.1); // Reduce repetition
    options.put("num_predict", 2048); // Ensure enough tokens for complete response
    request.put("options", options);

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

  private String sanitizeFullName(String rawName, String resumeText) {
    String name = rawName != null ? rawName.trim() : "";
    boolean looksLikeAddress = name.matches(
        ".*(Street|St\\.?|Road|Rd\\.?|Avenue|Ave\\.?|Lane|Ln\\.?|Blvd|Apartment|Apt|Suite|Unit|PO Box|P\\.?O\\.? Box).*?")
        || name.matches(".*\\d+.*");
    boolean tooLong = name.length() > 80;
    boolean missing = name.isEmpty();

    if (missing || looksLikeAddress || tooLong || name.contains("@")) {
      String inferred = inferName(resumeText);
      if (inferred != null && !inferred.isBlank()) {
        return inferred;
      }
      return "Unknown";
    }
    return name;
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
