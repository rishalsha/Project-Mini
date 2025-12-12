import React, { useState } from "react";
import { User, UserRole } from "../types";
import {
  loginUser,
  registerUser,
  fetchUserByEmail,
  loginEmployer,
  registerEmployer,
  fetchEmployerByEmail,
} from "../services/api";
import {
  Briefcase,
  User as UserIcon,
  Lock,
  Mail,
  ArrowRight,
  Sparkles,
  AlertCircle,
} from "lucide-react";

interface Props {
  onLogin: (user: User) => void;
}

const AuthPage: React.FC<Props> = ({ onLogin }) => {
  const [isLogin, setIsLogin] = useState(true);
  const [role, setRole] = useState<UserRole>("candidate");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      let authed: User | null = null;
      if (role === "candidate") {
        if (isLogin) {
          await loginUser(email, password);
        } else {
          await registerUser(name || email.split("@")[0], email, password);
        }
        authed = await fetchUserByEmail(email);
        authed.role = "candidate";
      } else {
        if (isLogin) {
          await loginEmployer(email, password);
        } else {
          await registerEmployer(name || email.split("@")[0], email, password);
        }
        authed = await fetchEmployerByEmail(email);
        authed.role = "employer";
      }

      if (authed) {
        onLogin(authed);
      } else {
        setError("Authentication failed");
      }
    } catch (err: any) {
      const msg = err?.message || "Authentication failed";
      if (msg.includes("Failed to fetch")) {
        setError(
          "Cannot reach backend. Start the API server and set VITE_API_URL if it's not http://localhost:8080."
        );
      } else if (
        msg.includes("Email already registered") ||
        msg.includes("409")
      ) {
        setError("This email is already registered. Please log in instead.");
      } else if (msg.includes("401")) {
        setError("Invalid email or password.");
      } else {
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="max-w-4xl w-full bg-white rounded-3xl shadow-2xl overflow-hidden flex flex-col md:flex-row min-h-[600px]">
        {/* Left Side: Branding */}
        <div className="md:w-1/2 bg-slate-900 p-12 flex flex-col justify-between relative overflow-hidden">
          <div className="absolute inset-0 bg-gradient-to-br from-indigo-600/20 to-purple-600/20 z-0"></div>
          <div className="absolute top-0 right-0 w-64 h-64 bg-indigo-500 rounded-full blur-[100px] opacity-20 transform translate-x-1/3 -translate-y-1/3"></div>

          <div className="relative z-10">
            <div className="flex items-center gap-2 mb-8">
              <div className="w-10 h-10 bg-indigo-500 rounded-xl flex items-center justify-center text-white">
                <Sparkles size={20} />
              </div>
              <span className="text-2xl font-bold text-white tracking-tight">
                AutoFolio
              </span>
            </div>

            <h2 className="text-4xl font-extrabold text-white mb-6 leading-tight">
              {role === "candidate"
                ? "Launch your career with an AI-powered portfolio."
                : "Access our curated talent pool."}
            </h2>
            <p className="text-slate-400 text-lg">
              {role === "candidate"
                ? "Upload your resume and get a professional website + job market analysis in seconds."
                : "Log in to browse pre-vetted candidates or screen new resumes instantly."}
            </p>
          </div>

          <div className="relative z-10 mt-12">
            <div className="flex gap-4">
              <button
                onClick={() => {
                  setRole("candidate");
                  setError(null);
                }}
                className={`flex-1 py-3 px-4 rounded-xl text-sm font-bold transition-all border-2 flex items-center justify-center gap-2 ${
                  role === "candidate"
                    ? "bg-white text-slate-900 border-white"
                    : "bg-transparent text-slate-400 border-slate-700 hover:border-slate-600"
                }`}
              >
                <UserIcon size={18} /> Candidate
              </button>
              <button
                onClick={() => {
                  setRole("employer");
                  setError(null);
                }}
                className={`flex-1 py-3 px-4 rounded-xl text-sm font-bold transition-all border-2 flex items-center justify-center gap-2 ${
                  role === "employer"
                    ? "bg-white text-slate-900 border-white"
                    : "bg-transparent text-slate-400 border-slate-700 hover:border-slate-600"
                }`}
              >
                <Briefcase size={18} /> Employer
              </button>
            </div>
          </div>
        </div>

        {/* Right Side: Form */}
        <div className="md:w-1/2 p-12 flex flex-col justify-center">
          <div className="mb-8">
            <h3 className="text-2xl font-bold text-slate-900 mb-2">
              {isLogin ? "Welcome back" : "Create an account"}
            </h3>
            <p className="text-slate-500">
              {isLogin
                ? "Enter your details to access your account."
                : `Sign up as a ${role} to get started.`}
            </p>
          </div>

          {error && (
            <div className="mb-6 bg-red-50 text-red-600 px-4 py-3 rounded-lg text-sm flex items-center gap-2 border border-red-100">
              <AlertCircle size={16} />
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {!isLogin && (
              <div className="space-y-1">
                <label className="text-xs font-bold text-slate-700 uppercase tracking-wide">
                  Full Name
                </label>
                <div className="relative">
                  <UserIcon
                    className="absolute left-3 top-3.5 text-slate-400"
                    size={18}
                  />
                  <input
                    type="text"
                    required
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none transition-all text-slate-900"
                    placeholder="John Doe"
                  />
                </div>
              </div>
            )}

            <div className="space-y-1">
              <label className="text-xs font-bold text-slate-700 uppercase tracking-wide">
                Email Address
              </label>
              <div className="relative">
                <Mail
                  className="absolute left-3 top-3.5 text-slate-400"
                  size={18}
                />
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none transition-all text-slate-900"
                  placeholder="name@example.com"
                />
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-bold text-slate-700 uppercase tracking-wide">
                Password
              </label>
              <div className="relative">
                <Lock
                  className="absolute left-3 top-3.5 text-slate-400"
                  size={18}
                />
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none transition-all text-slate-900"
                  placeholder="••••••••"
                />
              </div>
            </div>

            <button
              type="submit"
              className="w-full py-4 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl transition-all shadow-lg shadow-indigo-200 flex items-center justify-center gap-2 mt-4"
              disabled={loading}
            >
              {loading
                ? "Please wait..."
                : isLogin
                ? "Sign In"
                : "Create Account"}{" "}
              <ArrowRight size={18} />
            </button>
          </form>

          <div className="mt-8 text-center">
            <p className="text-slate-500 text-sm">
              {isLogin ? "Don't have an account?" : "Already have an account?"}
              <button
                onClick={() => {
                  setIsLogin(!isLogin);
                  setError(null);
                }}
                className="ml-2 font-bold text-indigo-600 hover:text-indigo-800"
              >
                {isLogin ? "Sign up" : "Log in"}
              </button>
            </p>
          </div>

          {/* Helper text for demo */}
          <div className="mt-4 p-3 bg-slate-100 rounded text-xs text-slate-500 text-center">
            <p>Demo Credentials:</p>
            <p>Candidate: candidate@demo.com / password</p>
            <p>Employer: employer@demo.com / password</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AuthPage;
