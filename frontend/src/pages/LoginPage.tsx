import React, { useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ApiError } from '../api/apiFetch';
import { Logo } from '../components/common/Logo';
import { ThemeToggle } from '../components/common/ThemeToggle';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const next = searchParams.get('next') || '/';

  const { login, isLoggingIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    try {
      await login({ email: email.trim(), password });
      navigate(next, { replace: true });
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setErrorMessage('Invalid email or password');
        } else if (err.status === 429) {
          setErrorMessage('Too many failed login attempts. Please wait 15 minutes before trying again.');
        } else {
          setErrorMessage(err.detail || 'An unexpected error occurred. Please try again.');
        }
      } else {
        setErrorMessage('Failed to connect to the server. Please check your connection.');
      }
    }
  };

  return (
    <div className="min-h-screen bg-[#f6f5f4] dark:bg-[#09090b] text-black dark:text-white flex flex-col justify-center py-12 sm:px-6 lg:px-8 relative transition-colors">
      <div className="absolute top-4 right-4">
        <ThemeToggle />
      </div>

      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="flex justify-center mb-3">
          <Logo size="lg" />
        </div>
        <h2 className="text-lg font-medium text-[#37352f] dark:text-[#e4e4e7]">Log in to your account</h2>
        <p className="mt-1 text-xs text-[#757575] dark:text-[#a1a1aa]">
          Sign in to access your sales datasets and KPI analytics.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4">
        <div className="bg-white dark:bg-[#0f0f10] py-8 px-6 shadow-xs border border-black/8 dark:border-white/10 rounded-xl sm:px-10">
          {errorMessage && (
            <div
              role="alert"
              className="mb-6 p-3 rounded-lg bg-red-50 border border-red-200 text-xs text-red-700 leading-relaxed"
            >
              {errorMessage}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="email" className="block text-xs font-medium text-[#37352f] mb-1">
                Email address
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@company.com"
                className="w-full px-3 py-2 text-sm bg-white border border-black/15 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] text-black placeholder:text-[#9e9e9e] transition-colors"
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-xs font-medium text-[#37352f] mb-1">
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                className="w-full px-3 py-2 text-sm bg-white border border-black/15 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] text-black placeholder:text-[#9e9e9e] transition-colors"
              />
            </div>

            <button
              type="submit"
              disabled={isLoggingIn}
              className="w-full mt-2 py-2 px-4 border border-transparent rounded-lg shadow-xs text-sm font-medium text-white bg-black hover:bg-[#2e2d2b] focus:outline-hidden focus:ring-2 focus:ring-offset-2 focus:ring-black disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {isLoggingIn ? 'Logging in...' : 'Sign in'}
            </button>
          </form>

          <div className="mt-6 pt-5 border-t border-black/8 text-center">
            <p className="text-xs text-[#757575]">
              Don't have an account?{' '}
              <Link
                to={`/register${next !== '/' ? `?next=${encodeURIComponent(next)}` : ''}`}
                className="font-medium text-[#0075de] hover:underline"
              >
                Sign up
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

