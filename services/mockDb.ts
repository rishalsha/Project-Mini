import { User, PortfolioData, ResumeAnalysis, CandidateProfile, UserRole } from '../types';

// Initial Mock Data
const MOCK_USERS: User[] = [
  { id: '1', email: 'candidate@demo.com', name: 'Alex Frontend', role: 'candidate', password: 'password' },
  { id: '2', email: 'employer@demo.com', name: 'Tech Recruiter', role: 'employer', password: 'password' },
  { id: '3', email: 'sarah@demo.com', name: 'Sarah Designer', role: 'candidate', password: 'password' },
];

const MOCK_PORTFOLIOS: Record<string, PortfolioData> = {
  '1': {
    fullName: "Alex Frontend",
    headline: "Senior React Developer | UI/UX Enthusiast",
    about: "Passionate frontend engineer with 5 years of experience building scalable web applications. I specialize in React, TypeScript, and modern CSS architectures.",
    location: "San Francisco, CA",
    email: "alex@demo.com",
    skills: [
      { name: "React", level: 95, category: "frontend" },
      { name: "TypeScript", level: 90, category: "frontend" },
      { name: "Node.js", level: 80, category: "backend" },
      { name: "Communication", level: 85, category: "soft-skills" }
    ],
    experience: [
      { company: "TechFlow Inc", role: "Senior Engineer", period: "2021-Present", description: "Leading the frontend team." }
    ],
    education: [],
    projects: []
  },
  '3': {
    fullName: "Sarah Designer",
    headline: "Product Designer & Frontend Dev",
    about: "Bridging the gap between design and code.",
    location: "New York, NY",
    email: "sarah@demo.com",
    skills: [
        { name: "Figma", level: 98, category: "design" },
        { name: "CSS", level: 90, category: "frontend" }
    ],
    experience: [],
    education: [],
    projects: []
  }
};

const MOCK_ANALYSES: Record<string, ResumeAnalysis> = {
  '1': {
    score: 88,
    summary: "Strong technical profile with clear progression.",
    strengths: ["Modern Tech Stack", "Leadership Experience"],
    weaknesses: ["Could add more metric-driven results"],
    marketOutlook: "High demand for Senior React Developers.",
    jobRecommendations: []
  },
  '3': {
    score: 82,
    summary: "Great hybrid profile.",
    strengths: ["Design Systems", "Prototyping"],
    weaknesses: ["Limited backend exposure"],
    marketOutlook: "Growing demand for UX Engineers.",
    jobRecommendations: []
  }
};

class MockDatabase {
  private users: User[] = [...MOCK_USERS];
  private portfolios: Record<string, PortfolioData> = { ...MOCK_PORTFOLIOS };
  private analyses: Record<string, ResumeAnalysis> = { ...MOCK_ANALYSES };

  login(email: string, password: string, role: UserRole): { success: boolean; user?: User; error?: string } {
    const user = this.users.find(u => u.email === email && u.password === password);
    
    if (!user) {
      return { success: false, error: 'Invalid email or password' };
    }

    if (user.role !== role) {
      return { success: false, error: `This account is registered as a ${user.role}, not a ${role}.` };
    }

    return { success: true, user };
  }

  register(email: string, password: string, name: string, role: UserRole): { success: boolean; user?: User; error?: string } {
    if (this.users.find(u => u.email === email)) {
      return { success: false, error: 'Email already exists' };
    }

    const newUser: User = {
      id: Math.random().toString(36).substr(2, 9),
      email,
      name,
      role,
      password
    };

    this.users.push(newUser);
    return { success: true, user: newUser };
  }

  saveCandidateData(userId: string, portfolio: PortfolioData, analysis: ResumeAnalysis) {
    this.portfolios[userId] = portfolio;
    this.analyses[userId] = analysis;
  }

  getCandidateData(userId: string): { portfolio: PortfolioData | null, analysis: ResumeAnalysis | null } {
    return {
      portfolio: this.portfolios[userId] || null,
      analysis: this.analyses[userId] || null
    };
  }

  getAllCandidates(): CandidateProfile[] {
    return this.users
      .filter(u => u.role === 'candidate')
      .map(user => ({
        user,
        portfolio: this.portfolios[user.id] || null,
        analysis: this.analyses[user.id] || null,
        lastUpdated: '2024-05-20' // Mock date
      }))
      // Filter out candidates who haven't uploaded anything yet
      .filter(profile => profile.portfolio !== null);
  }
}

export const db = new MockDatabase();