import { User, UserRole } from '../types';

export const API_BASE = (import.meta as any)?.env?.VITE_API_URL || 'http://localhost:8080';

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `Request failed with status ${res.status}`);
  }
  return res.json();
}

function mapUser(raw: any, role: UserRole): User {
  return {
    id: String(raw.id ?? raw.userId ?? ''),
    email: raw.email ?? '',
    name: raw.name ?? raw.fullName ?? '',
    role
  };
}

export async function loginUser(email: string, password: string): Promise<User> {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  const data = await handleResponse<any>(res);
  return mapUser(data, 'candidate');
}

export async function registerUser(name: string, email: string, password: string): Promise<User> {
  const res = await fetch(`${API_BASE}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, email, password })
  });
  const data = await handleResponse<any>(res);
  return mapUser(data, 'candidate');
}

export async function fetchUserByEmail(email: string): Promise<User> {
  const res = await fetch(`${API_BASE}/api/auth/user?email=${encodeURIComponent(email)}`);
  const data = await handleResponse<any>(res);
  return mapUser(data, 'candidate');
}

export async function loginEmployer(email: string, password: string): Promise<User> {
  const res = await fetch(`${API_BASE}/api/employer/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  const data = await handleResponse<any>(res);
  return mapUser(data, 'employer');
}

export async function registerEmployer(name: string, email: string, password: string, companyName?: string): Promise<User> {
  const res = await fetch(`${API_BASE}/api/employer/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, email, password, companyName })
  });
  const data = await handleResponse<any>(res);
  return mapUser(data, 'employer');
}

export async function fetchEmployerByEmail(email: string): Promise<User> {
  const res = await fetch(`${API_BASE}/api/employer/auth/employer?email=${encodeURIComponent(email)}`);
  const data = await handleResponse<any>(res);
  return mapUser(data, 'employer');
}

export async function getAllPortfolios(): Promise<any[]> {
  const res = await fetch(`${API_BASE}/api/portfolios`);
  return handleResponse<any[]>(res);
}

export async function getPortfolioByEmail(email: string): Promise<any> {
  const res = await fetch(`${API_BASE}/api/portfolios/by-email?email=${encodeURIComponent(email)}`);
  if (res.status === 404) return null; // no portfolio yet
  return handleResponse<any>(res);
}
