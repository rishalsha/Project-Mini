package com.portfolio.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
            You are an expert resume parser and portfolio builder. Extract comprehensive data from the provided resume and create a complete portfolio profile. Return ONLY valid JSON with NO empty sections.

            CRITICAL RULES:
            1. Return ONLY the JSON object - no markdown formatting (```json), no explanations, no extra text
            2. Extract the person's actual name from the resume (usually at the top) - NEVER use addresses, companies, or locations
            3. ALL fields are REQUIRED and MUST be populated - NEVER leave sections empty or with minimal content
            4. Be thorough - extract every skill, project, experience, and education mentioned
            5. When information is limited, create professional summaries based on available context
            6. Return consistent output - same resume should produce identical results

            MANDATORY JSON STRUCTURE (all fields required, in this exact order):
            {
              "fullName": "Person's complete name (usually first line/heading). NEVER use address or company name. Required.",
              
              "headline": "Professional title or role. Extract from: job title, career objective, or profile summary. If unclear, derive from most recent experience or skills (e.g., 'Software Developer', 'Data Analyst', 'Full Stack Engineer'). NEVER leave as generic 'Professional'.",
              
              "about": "Comprehensive professional summary (3-5 sentences minimum). MUST include:
                - Years of experience or education level
                - Key technical skills and expertise areas
                - Notable achievements or specializations
                - Career focus or professional goals
                Example: 'Experienced software developer with 5+ years building scalable web applications using React, Node.js, and PostgreSQL. Proven track record in delivering high-quality solutions for e-commerce and fintech industries. Passionate about clean code, performance optimization, and mentoring junior developers. Currently seeking opportunities to leverage expertise in cloud-native architectures and microservices.'
                NEVER use generic placeholders. Build from resume content.",
              
              "location": "Full location: City, State/Province, Country. Check for: address, location field, or infer from company locations. If truly not found: 'Location Not Specified'",
              
              "email": "Email address (check multiple locations: contact info, headers, footers). Required format: valid email. If not found: 'contact@notprovided.com'",
              
              "phone": "Phone with country code if available (e.g., '+1 (555) 123-4567'). If not found: 'Not Provided'",
              
              "linkedin": "Full LinkedIn profile URL (must be https://www.linkedin.com/in/...). Search entire resume. If not found: '' (empty string)",
              
              "github": "Full GitHub profile URL (https://github.com/username). Check portfolio, projects, links sections. If not found: '' (empty string)",
              
              "website": "Personal website or portfolio URL. Check multiple sections. If not found: '' (empty string)",
              
              "skills": [
                MANDATORY: Extract ALL skills mentioned anywhere in the resume. MUST have minimum 8-12 skills.
                {
                  "name": "Specific skill name (e.g., 'React', 'Python', 'Project Management')",
                  "level": NUMBER ONLY (no quotes, no ranges): 
                    - 85-95 for 5+ years or expert level
                    - 70-85 for 3-5 years or advanced level
                    - 55-70 for 1-3 years or intermediate
                    - 40-55 for <1 year or beginner
                    Pick single integer value (e.g., 85, not "85" or "70-90"),
                  "category": MUST be exactly one of: 'frontend', 'backend', 'design', 'soft-skills', 'tools', 'other'
                    Guidelines:
                    - frontend: React, Vue, Angular, HTML, CSS, JavaScript, TypeScript, UI/UX
                    - backend: Java, Python, Node.js, .NET, PHP, Ruby, databases, APIs
                    - design: Figma, Photoshop, UI Design, Graphic Design
                    - soft-skills: Leadership, Communication, Problem Solving, Teamwork
                    - tools: Git, Docker, Jenkins, AWS, Azure, Jira, VS Code
                    - other: anything else
                }
              ]
              Sources for skills: explicit skills section, technologies in projects, tools in experience, certifications.
              If fewer than 8 skills found, infer from job titles, education major, and project technologies.
              
              "experience": [
                Extract EVERY work experience, internship, freelance work mentioned. If present, minimum 1-3 entries.
                {
                  "company": "Full company/organization name. Required.",
                  "role": "Complete job title/position. Required.",
                  "period": "Full date range with month and year (e.g., 'January 2020 - December 2022', 'Jun 2019 - Present'). Required.",
                  "description": "COMPREHENSIVE description (4-8 sentences minimum). MUST include:
                    - Primary responsibilities and daily duties
                    - Technologies and tools used
                    - Key projects or initiatives led
                    - Measurable achievements (metrics, percentages, improvements)
                    - Team size or collaboration details if mentioned
                    - Impact on business or users
                    Extract from bullet points, paragraphs, and achievements sections.
                    Example: 'Led development of customer-facing web applications using React and Node.js, serving over 100,000 active users. Architected and implemented RESTful APIs with 99.9%% uptime. Collaborated with cross-functional teams of 8 developers and designers using Agile methodologies. Reduced page load time by 40%% through performance optimization. Mentored 3 junior developers and conducted code reviews. Implemented CI/CD pipelines using Jenkins and Docker, reducing deployment time by 50%%.'
                    NEVER use vague generic text like 'Responsible for various duties'."
                }
              ]
              If no experience found, return empty array [].
              
              "education": [
                Extract EVERY degree, certification, course, bootcamp mentioned. MUST have at least 1 entry if any education mentioned.
                {
                  "institution": "Full school/university/platform name (e.g., 'Stanford University', 'Coursera', 'FreeCodeCamp'). Required.",
                  "degree": "Complete degree with major/specialization (e.g., 'Bachelor of Science in Computer Science', 'Master of Business Administration', 'Full Stack Web Development Certificate'). Required.",
                  "year": "Graduation year or date range (e.g., '2018 - 2022', '2020'). If ongoing: 'Expected 2025'. Required."
                }
              ]
              Include: formal degrees, online certifications, bootcamps, relevant coursework.
              If truly no education found, return empty array [].
              
              "projects": [
                Extract EVERY project mentioned anywhere in resume. Include ALL: personal projects, work projects, academic projects, open source contributions.
                {
                  "name": "Full project name/title. Required.",
                  "description": "DETAILED description (3-6 sentences). MUST include:
                    - Project purpose and problem it solves
                    - Key features and functionality
                    - Technical implementation details
                    - Technologies and frameworks used
                    - Impact, results, or outcomes (users, performance, etc.)
                    - Your specific role and contributions
                    Example: 'E-commerce platform enabling small businesses to create online stores with zero coding. Features include product catalog management, payment processing via Stripe, real-time inventory tracking, and automated email notifications. Built with React frontend, Node.js/Express backend, and PostgreSQL database. Implemented JWT authentication and RESTful API architecture. Successfully deployed to AWS with 500+ active merchants and processing $100K+ monthly transactions. Solely responsible for full-stack development and deployment.'
                    NEVER use placeholder text.",
                  "technologies": ["List ALL technologies, frameworks, languages, tools used. Minimum 2-5 items per project. Examples: 'React', 'Node.js', 'PostgreSQL', 'AWS', 'Docker'. MUST be array, never empty."],
                  "link": "Full URL if available (GitHub repo, live demo, portfolio link). Otherwise empty string ''. Check for: github links, demo URLs, portfolio references."
                }
              ]
              Look in: projects section, work experience (work projects), education (academic projects), certifications.
              If no projects mentioned, return empty array [].
            }

            VALIDATION REQUIREMENTS - Check before returning:
            ✓ fullName: actual person name, not company/address
            ✓ headline: specific role, not generic
            ✓ about: 3-5 detailed sentences with concrete information
            ✓ skills: minimum 8-12 entries with proper levels (integers only) and categories
            ✓ experience: if present, each has detailed 4-8 sentence descriptions
            ✓ education: at least 1 entry if any education mentioned
            ✓ projects: if present, each has detailed 3-6 sentence descriptions with technologies array
            ✓ All required fields present in exact order
            ✓ No generic placeholders in critical fields
            ✓ skill levels are plain integers (e.g., 85) not strings or ranges

            Resume content:
            %s

            OUTPUT FORMAT: Return ONLY the complete JSON object. No markdown code blocks, no explanations, no extra text. Start with { and end with }.
            """,
        resumeText);

    System.out.println(resumeText);

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
            You are an expert career advisor and resume analyst with deep knowledge of hiring trends, ATS systems, and professional development. Provide a comprehensive, actionable analysis of this candidate's resume.

            CRITICAL RULES:
            1. Return ONLY valid JSON - no markdown formatting (```json), no explanations, no extra text before or after
            2. ALL fields are MANDATORY and MUST be thoroughly populated - NEVER leave any field empty or with generic content
            3. Be specific, constructive, and actionable in all feedback
            4. Base analysis on actual resume content, not assumptions
            5. Provide professional, encouraging yet honest assessment
            6. Follow exact field order and structure shown below

            REQUIRED JSON STRUCTURE (all fields mandatory, in this exact order):
            {
              "score": Calculate holistic professional score (40-95, integer only):
                Scoring breakdown:
                - Resume structure and clarity (0-20 points): formatting, organization, readability, ATS-friendliness
                - Quantifiable achievements (0-25 points): metrics, numbers, measurable impact, results-driven content
                - Skills relevance and depth (0-25 points): technical skills breadth, current technologies, skill alignment with career level
                - Experience quality (0-20 points): role progression, responsibilities depth, industry relevance, tenure
                - Completeness (0-10 points): contact info, education, projects, certifications
                
                Typical ranges:
                - 85-95: Exceptional resume, senior-level with strong achievements
                - 75-84: Strong resume, clear career progression with good metrics
                - 65-74: Solid resume, room for improvement in quantification or depth
                - 55-64: Average resume, needs work on structure and content
                - 40-54: Needs significant improvement across multiple areas
                
                Return single integer (e.g., 72, not "72" or 70-75),

              "summary": "Comprehensive 4-6 sentence professional assessment. MUST include:
                - Overall impression and strongest aspects of candidacy
                - Career level assessment with justification (entry-level 0-2 yrs, mid-level 3-5 yrs, senior 5-10 yrs, lead/expert 10+ yrs)
                - Key differentiators or standout qualities that make candidate competitive
                - Primary focus areas for improvement with strategic reasoning
                - Market readiness and hiring potential assessment
                
                Example: 'This candidate demonstrates strong technical capabilities as a mid-level full-stack developer with 4 years of progressive experience. The resume effectively showcases modern tech stack proficiency (React, Node.js, AWS) and includes several well-documented projects. Strongest assets are the detailed project descriptions and quantifiable achievements in performance optimization. Primary improvement area is adding more business impact metrics to work experience sections. The candidate shows excellent growth trajectory and would be competitive for mid-level to senior positions at tech companies and startups. With enhanced metrics, this profile could command 15-20%% salary premium.'
                
                NEVER use generic text. Build specific assessment from resume content.",

              "strengths": [
                "List 4-6 specific, concrete strengths with evidence from the resume",
                "Each strength MUST reference actual resume content (technologies, experiences, achievements, projects)",
                "Focus on:",
                "  - Technical skills mastery with specific technologies mentioned",
                "  - Quantified achievements with numbers/percentages",
                "  - Career progression and role advancement",
                "  - Project complexity and impact",
                "  - Modern/in-demand skills and tools",
                "  - Education credentials and certifications",
                "  - Industry-specific expertise",
                "  - Leadership or mentorship examples",
                "  - Problem-solving demonstrations",
                
                "Examples of GOOD strengths:",
                "  'Demonstrates expertise in modern frontend development with React, TypeScript, and Next.js across multiple production projects'",
                "  'Shows measurable impact with 40%% performance improvement and 99.9%% uptime achievement in previous role'",
                "  'Clear career progression from Junior to Senior Developer within 5 years, indicating strong growth potential'",
                "  'Impressive portfolio of 8 personal projects showcasing full-stack capabilities and entrepreneurial mindset'",
                "  'Strong foundation with Computer Science degree from accredited university plus AWS certification'",
                
                "Examples of BAD strengths (too generic):",
                "  'Has good skills' ❌",
                "  'Professional formatting' ❌",
                "  'Contact information provided' ❌",
                
                "MUST contain 4-6 detailed items. NEVER use generic placeholders.",
                "If resume is weak, focus on: 'foundational skills present', 'clear communication in descriptions', 'relevant education for career path', 'shows initiative through personal projects'"
              ],

              "weaknesses": [
                "List 4-7 specific, actionable areas for improvement - this field is ABSOLUTELY CRITICAL",
                "Each weakness MUST include:",
                "  1. What is missing or inadequate",
                "  2. Why it matters for job search success",
                "  3. Specific action to take (the 'how to fix')",
                
                "Common improvement areas to assess:",
                "  - Missing quantifiable metrics and KPIs in experience descriptions",
                "  - Lack of business impact or ROI statements",
                "  - Insufficient technical skills for target role",
                "  - Missing modern/trending technologies",
                "  - Vague or generic responsibility descriptions",
                "  - No leadership or mentorship examples",
                "  - Limited project portfolio or outdated projects",
                "  - Gaps in resume timeline not explained",
                "  - Missing certifications relevant to field",
                "  - Weak or absent professional summary",
                "  - No links to GitHub, portfolio, or LinkedIn",
                "  - Projects lack technical depth or outcome metrics",
                "  - Education section incomplete or unclear",
                "  - Resume length issues (too short/long)",
                "  - ATS optimization problems (formatting, keywords)",
                
                "Examples of GOOD, actionable weaknesses:",
                "  'Experience descriptions lack quantifiable metrics - add specific numbers like user counts, performance improvements (%%,x), revenue impact, or team sizes to demonstrate measurable value and make accomplishments more compelling to hiring managers'",
                "  'Missing cloud platform expertise (AWS/Azure/GCP) which is required for 70%% of backend positions - recommend obtaining AWS Solutions Architect Associate certification and building a cloud-deployed project to fill this critical gap'",
                "  'Projects section missing technical architecture details - expand descriptions to include system design decisions, scalability considerations, and trade-offs made to showcase senior-level thinking'",
                "  'No evidence of leadership or mentorship despite 6+ years experience - add examples of code reviews, onboarding new team members, or technical presentations to demonstrate senior-level soft skills'",
                "  'LinkedIn and GitHub links missing - these are critical for tech roles as 85%% of recruiters review them; create profiles and add links prominently'",
                "  'Resume uses outdated technologies (jQuery, PHP 5) without mentioning modern alternatives - highlight recent projects with current frameworks (React, Node.js) or invest time learning trending technologies'",
                
                "Examples of BAD weaknesses (too vague):",
                "  'Could be better' ❌",
                "  'Needs improvement' ❌",
                "  'Limited experience' (without specifics) ❌",
                
                "MUST contain 4-7 substantive, actionable items. This field is MANDATORY and CRITICAL.",
                "Even for strong resumes, find areas like: 'Could emphasize leadership more', 'Add metrics to recent projects', 'Include certification pursuit', 'Expand technical blog or speaking experience'",
                "NEVER return empty array or generic comments. This is the MOST VALUABLE section for the user."
              ],

              "marketOutlook": "Comprehensive 4-5 sentence market analysis. MUST address:
                - Current demand for candidate's primary skills in job market (high/medium/low demand)
                - Industry trends affecting their career path (growing/stable/declining sectors)
                - Salary expectations for their experience level and location (ranges if possible)
                - Competition level for their profile (how many similar candidates)
                - Recommended skills to acquire for better opportunities (specific technologies/certifications)
                - Emerging opportunities in their field (new roles, industries, specializations)
                
                Example: 'The skills demonstrated (React, Node.js, Python) are in high demand with 45,000+ job openings currently in the US market. Full-stack developers with 3-5 years experience can expect $90K-$130K salary range depending on location and company size. The market is competitive but favorable for candidates with strong portfolios and modern tech stacks. Strong growth trajectory predicted for cloud-native development and AI/ML integration skills. Recommend adding AWS certification and exploring microservices architecture to increase marketability by 30-40%%. Consider specializing in either frontend (React ecosystem) or backend (distributed systems) to reach senior level faster. Remote opportunities abundant, with 60%% of companies now offering remote or hybrid positions.'
                
                MUST provide specific, researched insights. NEVER use generic text like 'Skills show relevance'.",

              "jobRecommendations": [
                "Provide 3-5 specific job recommendations tailored to candidate's profile",
                "Each recommendation MUST be realistic and aligned with their experience level",
                {
                  "title": "Specific, realistic job title matching their exact skills and experience level. Use actual job titles from market:
                    - Entry-level (0-2 yrs): 'Junior Full Stack Developer', 'Frontend Developer I', 'Associate Software Engineer'
                    - Mid-level (3-5 yrs): 'Full Stack Developer', 'Senior Frontend Engineer', 'Backend Developer'  
                    - Senior (5-10 yrs): 'Senior Software Engineer', 'Lead Developer', 'Engineering Manager'
                    - Expert (10+ yrs): 'Principal Engineer', 'Director of Engineering', 'Technical Architect'
                    
                    Consider their tech stack: 'React Developer', 'Node.js Backend Engineer', 'Python Data Engineer', 'DevOps Engineer', 'Mobile Developer', etc.",
                    
                  "company": "Realistic company types or specific industries that match their background:
                    Examples: 'Early-stage startups (10-50 employees)', 'Series B SaaS companies', 'Fortune 500 tech companies', 'Financial services firms', 'Healthcare technology companies', 'E-commerce platforms', 'Digital agencies', 'Consulting firms (Accenture, Deloitte)', 'FAANG companies', 'Remote-first tech startups'
                    
                    Be specific about company stage, size, and industry based on their experience.",
                    
                  "location": "Realistic job locations based on their skills and market:
                    - For tech roles: 'Remote (US-based)', 'San Francisco Bay Area', 'New York City', 'Seattle', 'Austin, TX', 'Boston', 'Denver', 'Major tech hubs', 'Hybrid - Bay Area'
                    - For other roles: mention specific cities/regions relevant to their industry
                    - Consider mentioning: 'Remote opportunities available', 'Relocate to tech hub recommended', 'Local opportunities in [their city]'",
                    
                  "matchReason": "Detailed explanation (3-4 sentences) of why this role is an excellent fit. MUST include:
                    - Specific skills from resume that match job requirements
                    - How their experience level aligns with role expectations
                    - Why this company type suits their background
                    - Growth potential and next career step this enables
                    - Salary range expectations if applicable
                    
                    Example: 'Your 4 years of React and Node.js experience with demonstrated full-stack projects makes you an ideal candidate for mid-level full-stack roles at growing startups. These companies value your ability to work across the entire stack and ship features independently, which you've proven through your e-commerce platform project. Series B startups typically offer $110K-$140K plus equity for this experience level and provide rapid growth opportunities to senior roles within 1-2 years. Your AWS experience is particularly valuable as these companies are scaling their infrastructure.'"
                }
              ]
              Base recommendations on: their actual skills, experience level, career trajectory, location preferences if mentioned, and current market demand.
              NEVER recommend positions too junior or too senior for their background.
            }

            FINAL VALIDATION CHECKLIST - Confirm ALL before returning:
            ✓ score: integer 40-95 based on detailed criteria
            ✓ summary: 4-6 substantive sentences with specific observations
            ✓ strengths: 4-6 specific items with resume evidence, NO generic placeholders
            ✓ weaknesses: 4-7 detailed, actionable items with "what, why, how" - NEVER empty
            ✓ marketOutlook: 4-5 sentences with specific market data and recommendations
            ✓ jobRecommendations: 3-5 entries, each with realistic details and thorough matchReason
            ✓ All fields present in exact order specified
            ✓ No null, empty, or generic placeholder values
            ✓ weaknesses array is comprehensive and actionable (CRITICAL REQUIREMENT)

            Resume content:
            %s

            OUTPUT FORMAT: Return ONLY the complete JSON object. No markdown code blocks (no ```json), no explanations, no preamble. Start directly with { and end with }.
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
    options.put("temperature", 0.4); // Balanced temperature for consistency while maintaining quality
    options.put("top_p", 0.95); // Higher nucleus sampling for better coverage
    options.put("top_k", 50); // Increased token selection for richer output
    options.put("repeat_penalty", 1.15); // Stronger penalty to reduce repetition
    options.put("num_predict", 10000); // Increased token limit for comprehensive responses
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
      // Normalize model quirks before strict deserialization
      if (clazz == com.portfolio.backend.dto.PortfolioData.class) {
        // First, lenient text-level fixes to make JSON parseable
        jsonText = normalizeSkillLevelsLenient(jsonText);
        // Then, strict normalization via JSON tree
        jsonText = normalizeSkillLevels(jsonText);
      }
      return objectMapper.readValue(jsonText, clazz);
    }

    throw new Exception("No valid JSON found in response");
  }

  /**
   * Lenient normalization using regex so obviously invalid forms (e.g., level:
   * 70-90 without quotes)
   * become parseable JSON before tree-based processing.
   */
  private String normalizeSkillLevelsLenient(String jsonText) {
    try {
      // Pass 1: level: 70-90 (no quotes)
      jsonText = replaceLevelPattern(jsonText, "\\\"level\\\"\\s*:\\s*(\\d{1,3})\\s*[-–]\\s*(\\d{1,3})", false);
      // Pass 2: level: \"70-90\"
      jsonText = replaceLevelPattern(jsonText, "\\\"level\\\"\\s*:\\s*\\\"(\\d{1,3})\\s*[-–]\\s*(\\d{1,3})\\\"", true);
      // Pass 3: level: \"80%\"
      jsonText = replaceSingleNumberPattern(jsonText, "\\\"level\\\"\\s*:\\s*\\\"(\\d{1,3})%\\\"", true);
      // Pass 4: level: \"80\"
      jsonText = replaceSingleNumberPattern(jsonText, "\\\"level\\\"\\s*:\\s*\\\"(\\d{1,3})\\\"", true);
    } catch (Exception e) {
      System.err.println("normalizeSkillLevelsLenient(): " + e.getMessage());
    }
    return jsonText;
  }

  private String replaceLevelPattern(String input, String regex, boolean quoted) {
    StringBuffer sb = new StringBuffer();
    Matcher m = Pattern.compile(regex).matcher(input);
    while (m.find()) {
      int a = Integer.parseInt(m.group(1));
      int b = Integer.parseInt(m.group(2));
      int avg = Math.max(0, Math.min(100, (a + b) / 2));
      String replacement = "\"level\": " + avg;
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private String replaceSingleNumberPattern(String input, String regex, boolean quoted) {
    StringBuffer sb = new StringBuffer();
    Matcher m = Pattern.compile(regex).matcher(input);
    while (m.find()) {
      int n = Integer.parseInt(m.group(1));
      int v = Math.max(0, Math.min(100, n));
      String replacement = "\"level\": " + v;
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * Strict normalization via JSON tree: converts textual level values to
   * integers.
   */
  private String normalizeSkillLevels(String jsonText) {
    try {
      JsonNode root = objectMapper.readTree(jsonText);
      if (root instanceof ObjectNode objectNode) {
        JsonNode skillsNode = objectNode.get("skills");
        if (skillsNode instanceof ArrayNode arrayNode) {
          for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode skill = arrayNode.get(i);
            if (skill instanceof ObjectNode skillObj) {
              JsonNode levelNode = skillObj.get("level");
              if (levelNode != null && levelNode.isTextual()) {
                int normalized = coerceLevelToInt(levelNode.asText());
                skillObj.set("level", IntNode.valueOf(normalized));
              }
            }
          }
        }
      }
      return objectMapper.writeValueAsString(root);
    } catch (Exception e) {
      System.err.println("normalizeSkillLevels(): " + e.getMessage());
      return jsonText;
    }
  }

  /**
   * Extracts an integer from textual level representations. Handles:
   * - Ranges like "70-90" (uses average)
   * - Percent like "80%" (uses 80)
   * - Descriptive text containing a number, uses the first number
   * Bounds result to [0, 100].
   */
  private int coerceLevelToInt(String text) {
    if (text == null || text.isBlank())
      return 60; // reasonable default
    try {
      // Range pattern: e.g., 70-90
      Matcher range = Pattern.compile("(\\d{1,3})\\s*[-–]\\s*(\\d{1,3})").matcher(text);
      if (range.find()) {
        int a = Integer.parseInt(range.group(1));
        int b = Integer.parseInt(range.group(2));
        int avg = (a + b) / 2;
        return Math.max(0, Math.min(100, avg));
      }
      // Percent or plain number embedded
      Matcher num = Pattern.compile("(\\d{1,3})").matcher(text);
      if (num.find()) {
        int n = Integer.parseInt(num.group(1));
        return Math.max(0, Math.min(100, n));
      }
    } catch (Exception ignored) {
    }
    return 60;
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
