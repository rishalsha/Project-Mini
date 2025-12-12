import { GoogleGenAI, Type } from "@google/genai";
import { PortfolioData, ResumeAnalysis } from "../types";

// Initialize Gemini Client
const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });

/**
 * Parses the resume (text, image, or PDF) to extract structured portfolio data.
 */
export const parseResumeToPortfolio = async (
  input: string,
  mimeType: string
): Promise<PortfolioData> => {
  const model = "gemini-2.5-flash";
  
  const prompt = `
    You are an expert UI/UX developer and resume parser. 
    Extract data from the provided resume to populate a professional portfolio website.
    Return ONLY valid JSON matching the schema.
    
    Ensure 'skills' includes a 'level' (0-100 estimate based on context) and 'category'.
    Make the 'about' section a compelling professional bio suitable for a website hero section.
    If specific fields (like links) are missing, leave them as empty strings or empty arrays.
  `;

  const parts = [];
  if (mimeType.startsWith('text/')) {
    parts.push({ text: input });
  } else {
    // For Images and PDF
    parts.push({
      inlineData: {
        mimeType: mimeType,
        data: input.split(",")[1], // Remove data URL prefix
      },
    });
  }
  parts.push({ text: prompt });

  const response = await ai.models.generateContent({
    model,
    contents: { parts },
    config: {
      responseMimeType: "application/json",
      responseSchema: {
        type: Type.OBJECT,
        properties: {
          fullName: { type: Type.STRING },
          headline: { type: Type.STRING },
          about: { type: Type.STRING },
          location: { type: Type.STRING },
          email: { type: Type.STRING },
          phone: { type: Type.STRING },
          linkedin: { type: Type.STRING },
          github: { type: Type.STRING },
          website: { type: Type.STRING },
          skills: {
            type: Type.ARRAY,
            items: {
              type: Type.OBJECT,
              properties: {
                name: { type: Type.STRING },
                level: { type: Type.INTEGER },
                category: { type: Type.STRING, enum: ['frontend', 'backend', 'design', 'soft-skills', 'tools', 'other'] },
              },
            },
          },
          experience: {
            type: Type.ARRAY,
            items: {
              type: Type.OBJECT,
              properties: {
                company: { type: Type.STRING },
                role: { type: Type.STRING },
                period: { type: Type.STRING },
                description: { type: Type.STRING },
              },
            },
          },
          education: {
            type: Type.ARRAY,
            items: {
              type: Type.OBJECT,
              properties: {
                institution: { type: Type.STRING },
                degree: { type: Type.STRING },
                year: { type: Type.STRING },
              },
            },
          },
          projects: {
            type: Type.ARRAY,
            items: {
              type: Type.OBJECT,
              properties: {
                name: { type: Type.STRING },
                description: { type: Type.STRING },
                technologies: { type: Type.ARRAY, items: { type: Type.STRING } },
                link: { type: Type.STRING },
              },
            },
          },
        },
      },
    },
  });

  if (!response.text) throw new Error("Failed to parse resume.");
  
  const rawData = JSON.parse(response.text) as Partial<PortfolioData>;
  
  // Sanitize and provide defaults to prevent "undefined map" errors
  return {
    fullName: rawData.fullName || "Portfolio",
    headline: rawData.headline || "",
    about: rawData.about || "",
    location: rawData.location || "",
    email: rawData.email || "",
    phone: rawData.phone,
    linkedin: rawData.linkedin,
    github: rawData.github,
    website: rawData.website,
    skills: Array.isArray(rawData.skills) ? rawData.skills : [],
    experience: Array.isArray(rawData.experience) ? rawData.experience : [],
    education: Array.isArray(rawData.education) ? rawData.education : [],
    projects: Array.isArray(rawData.projects) ? rawData.projects : [],
  };
};

/**
 * Analyzes the resume and performs a live job search.
 */
export const analyzeResumeAndSearchJobs = async (
  input: string,
  mimeType: string
): Promise<ResumeAnalysis> => {
  const model = "gemini-2.5-flash";
  
  const prompt = `
    Analyze this candidate's profile.
    1. Give a 'score' (0-100) based on clarity, impact, and completeness.
    2. Provide a 'summary' critique.
    3. List 'strengths' and 'weaknesses'.
    4. Provide a 'marketOutlook' paragraph describing the current demand for these skills.
    5. USE THE GOOGLE SEARCH TOOL to find 4-5 REAL, CURRENT job openings that match this profile.
       For each job, provide the title, company, location, and a reason why it's a good match.
       Try to find links if possible, otherwise describe where to find them.
       
    Return the result as JSON.
  `;

  const parts = [];
  if (mimeType.startsWith('text/')) {
    parts.push({ text: input });
  } else {
    parts.push({
      inlineData: {
        mimeType: mimeType,
        data: input.split(",")[1],
      },
    });
  }
  parts.push({ text: prompt });

  const response = await ai.models.generateContent({
    model,
    contents: { parts },
    config: {
      tools: [{ googleSearch: {} }],
      systemInstruction: "You are a career coach. You MUST return your final response as a valid JSON object. Do not include markdown formatting like ```json ... ``` in the response, just the raw JSON string.",
    },
  });

  let text = response.text || "{}";
  text = text.replace(/```json/g, "").replace(/```/g, "").trim();

  try {
    const rawData = JSON.parse(text) as Partial<ResumeAnalysis>;
    return {
      score: typeof rawData.score === 'number' ? rawData.score : 0,
      summary: rawData.summary || "Analysis unavailable.",
      strengths: Array.isArray(rawData.strengths) ? rawData.strengths : [],
      weaknesses: Array.isArray(rawData.weaknesses) ? rawData.weaknesses : [],
      marketOutlook: rawData.marketOutlook || "Market data unavailable.",
      jobRecommendations: Array.isArray(rawData.jobRecommendations) ? rawData.jobRecommendations : [],
    };
  } catch (e) {
    console.error("Failed to parse analysis JSON", text);
    return {
      score: 0,
      summary: "Could not parse analysis. Please try again.",
      strengths: [],
      weaknesses: [],
      marketOutlook: "N/A",
      jobRecommendations: []
    };
  }
};