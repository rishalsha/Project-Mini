import React, { useState, useEffect } from "react";
import UploadSection from "./components/UploadSection";
import PortfolioView from "./components/PortfolioView";
import AuthPage from "./components/AuthPage";
import EmployerDashboard from "./components/EmployerDashboard";
import {
  PortfolioData,
  ResumeAnalysis,
  ViewMode,
  User,
  CandidateProfile,
} from "./types";
import { db } from "./services/mockDb";
import { Eye, EyeOff, LogOut, User as UserIcon, ArrowLeft } from "lucide-react";

const API_URL = "http://localhost:8080/api/resume";

const App: React.FC = () => {
  const [user, setUser] = useState<User | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>("upload");
  const [portfolioData, setPortfolioData] = useState<PortfolioData | null>(
    null
  );
  const [analysisData, setAnalysisData] = useState<ResumeAnalysis | null>(null);

  useEffect(() => {
    if (user) {
      if (user.role === "candidate") {
        // Load candidate's saved data if it exists
        const { portfolio, analysis } = db.getCandidateData(user.id);
        if (portfolio) {
          setPortfolioData(portfolio);
          setAnalysisData(analysis);
          setViewMode("portfolio");
        } else {
          setViewMode("upload");
        }
      } else {
        // Employer defaults to dashboard
        setViewMode("employer-dashboard");
      }
    }
  }, [user]);

  const handleUpload = async (content: string, mimeType: string) => {
    setViewMode("analyzing");
    try {
      const formData = new FormData();

      if (mimeType.startsWith("text/")) {
        // Send as text parameter
        formData.append("text", content);
      } else {
        // Convert base64 to file for PDF/DOCX
        const base64Data = content.split(",")[1];
        const byteCharacters = atob(base64Data);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const byteArray = new Uint8Array(byteNumbers);
        const blob = new Blob([byteArray], { type: mimeType });
        const filename = mimeType.includes("pdf")
          ? "resume.pdf"
          : "resume.docx";
        formData.append("file", blob, filename);
      }

      const response = await fetch(`${API_URL}/parse`, {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        throw new Error("Failed to parse resume");
      }

      const data = await response.json();
      const portfolio = data.portfolio;
      const analysis = data.analysis;

      setPortfolioData(portfolio);
      setAnalysisData(analysis);

      // Save data if user is a candidate
      if (user?.role === "candidate") {
        db.saveCandidateData(user.id, portfolio, analysis);
        setViewMode("portfolio");
      } else {
        // Employer is just screening, set view mode but don't save to their own ID
        setViewMode("portfolio"); // This will be rendered as employer view due to logic below
      }
    } catch (error) {
      console.error("Error processing resume:", error);
      alert(
        "Something went wrong while processing the resume. Please try again."
      );
      setViewMode("upload");
    }
  };

  const handleSelectCandidate = (profile: CandidateProfile) => {
    setPortfolioData(profile.portfolio);
    setAnalysisData(profile.analysis);
    setViewMode("portfolio");
  };

  const handleLogout = () => {
    setUser(null);
    setPortfolioData(null);
    setAnalysisData(null);
    setViewMode("upload");
  };

  const toggleView = () => {
    if (!portfolioData) return;
    if (viewMode === "portfolio") {
      setViewMode("employer");
    } else {
      setViewMode("portfolio");
    }
  };

  // If not logged in, show Auth Page
  if (!user) {
    return <AuthPage onLogin={setUser} />;
  }

  // Determine if we are in the "Employer's view of a Candidate"
  // Logic:
  // 1. If logged in as Employer, they always see EmployerView (unless on dashboard/upload)
  // 2. If logged in as Candidate, they can toggle via viewMode='employer'
  const isEmployerView = user.role === "employer" || viewMode === "employer";

  return (
    <div className="relative">
      {/* App Header */}
      <div className="fixed top-0 left-0 right-0 h-16 bg-white/0 z-50 flex items-center justify-between px-6 pointer-events-none">
        <div className="pointer-events-auto bg-white/90 backdrop-blur rounded-full px-4 py-2 shadow-sm border border-slate-200 mt-4 flex items-center gap-2">
          <div className="w-8 h-8 bg-indigo-100 rounded-full flex items-center justify-center text-indigo-600">
            <UserIcon size={16} />
          </div>
          <div className="text-sm">
            <span className="font-bold text-slate-900 block leading-tight">
              {user.name}
            </span>
            <span className="text-xs text-slate-500 uppercase tracking-wide font-semibold">
              {user.role} Account
            </span>
          </div>
        </div>

        <div className="pointer-events-auto flex items-center gap-4">
          {/* Back to Dashboard Button for Employers */}
          {user.role === "employer" && viewMode !== "employer-dashboard" && (
            <button
              onClick={() => {
                setPortfolioData(null);
                setAnalysisData(null);
                setViewMode("employer-dashboard");
              }}
              className="bg-white/90 backdrop-blur rounded-full px-4 py-2 shadow-sm border border-slate-200 mt-4 text-sm font-bold text-slate-600 hover:text-indigo-600 hover:bg-indigo-50 transition-colors flex items-center gap-2"
            >
              <ArrowLeft size={16} /> Back to Dashboard
            </button>
          )}

          <button
            onClick={handleLogout}
            className="bg-white/90 backdrop-blur rounded-full px-4 py-2 shadow-sm border border-slate-200 mt-4 text-sm font-bold text-slate-600 hover:text-red-600 hover:bg-red-50 transition-colors flex items-center gap-2"
          >
            <LogOut size={16} /> Sign Out
          </button>
        </div>
      </div>

      {/* Main Content Router */}
      {viewMode === "employer-dashboard" && user.role === "employer" && (
        <EmployerDashboard
          onSelectCandidate={handleSelectCandidate}
          onScreenNew={() => setViewMode("upload")}
        />
      )}

      {(viewMode === "upload" || viewMode === "analyzing") && (
        <UploadSection
          onUpload={handleUpload}
          isLoading={viewMode === "analyzing"}
          isEmployer={user.role === "employer"}
        />
      )}

      {(viewMode === "portfolio" || viewMode === "employer") &&
        portfolioData && (
          <>
            <PortfolioView
              data={portfolioData}
              analysis={analysisData}
              isEmployerView={isEmployerView}
            />

            {/* Floating Toggle - Only show for Candidate */}
            {user.role === "candidate" && (
              <div
                className="fixed bottom-6 left-1/2 -translate-x-1/2 z-[60] bg-slate-900/90 backdrop-blur text-white px-6 py-3 rounded-full shadow-2xl flex items-center gap-6 cursor-pointer hover:scale-105 transition-transform"
                onClick={toggleView}
              >
                <div
                  className="flex items-center gap-2 text-sm font-medium text-slate-200 hover:text-white transition-colors"
                  title="Toggle View Mode"
                >
                  {viewMode === "portfolio" ? (
                    <EyeOff size={16} />
                  ) : (
                    <Eye size={16} />
                  )}
                  {viewMode === "portfolio"
                    ? "Preview as Employer"
                    : "Back to My Analysis"}
                </div>
              </div>
            )}
          </>
        )}
    </div>
  );
};

export default App;
