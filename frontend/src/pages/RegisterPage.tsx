import React, { useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ApiError } from '../api/apiFetch';
import { Logo } from '../components/common/Logo';
import { ThemeToggle } from '../components/common/ThemeToggle';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const next = searchParams.get('next') || '/';

  const { register, isRegistering } = useAuth();
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const validateClient = (): boolean => {
    const errors: Record<string, string> = {};
    const trimmedName = displayName.trim();
    const trimmedEmail = email.trim();

    if (!trimmedName) {
      errors.displayName = 'Display name is required';
    } else if (trimmedName.length > 80) {
      errors.displayName = 'Display name must not exceed 80 characters';
    }

    if (!trimmedEmail) {
      errors.email = 'Email address is required';
    } else if (trimmedEmail.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      errors.email = 'Please enter a valid email address';
    }

    const passwordBytes = new TextEncoder().encode(password).length;
    if (!password) {
      errors.password = 'Password is required';
    } else if (passwordBytes < 10) {
      errors.password = 'Password must be at least 10 characters';
    } else if (passwordBytes > 72) {
      errors.password = 'Password must not exceed 72 bytes';
    } else if (password.toLowerCase() === trimmedEmail.toLowerCase()) {
      errors.password = 'Password cannot be the same as your email';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setGeneralError(null);
    setFieldErrors({});

    if (!validateClient()) {
      return;
    }

    try {
      await register({
        displayName: displayName.trim(),
        email: email.trim(),
        password,
      });
      navigate(next, { replace: true });
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setGeneralError('An account with this email address already exists. Please log in instead.');
        } else if (err.status === 400 && err.fieldErrors && err.fieldErrors.length > 0) {
          const map: Record<string, string> = {};
          err.fieldErrors.forEach((fe) => {
            map[fe.field] = fe.message;
          });
          setFieldErrors(map);
        } else {
          setGeneralError(err.detail || 'Registration failed. Please check your inputs.');
        }
      } else {
        setGeneralError('Failed to connect to the server. Please check your connection.');
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
        <h2 className="text-lg font-medium text-[#37352f] dark:text-[#e4e4e7]">Create your account</h2>
        <p className="mt-1 text-xs text-[#757575] dark:text-[#a1a1aa]">
          Get started with Mutex Business Intelligence & Analytics.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4">
        <div className="bg-white dark:bg-[#0f0f10] py-8 px-6 shadow-xs border border-black/8 dark:border-white/10 rounded-xl sm:px-10">
          {generalError && (
            <div
              role="alert"
              className="mb-6 p-3 rounded-lg bg-red-50 border border-red-200 text-xs text-red-700 leading-relaxed"
            >
              {generalError}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="displayName" className="block text-xs font-medium text-[#37352f] mb-1">
                Full Name
              </label>
              <input
                id="displayName"
                name="displayName"
                type="text"
                autoComplete="name"
                required
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="Jane Doe"
                className="w-full px-3 py-2 text-sm bg-white border border-black/15 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] text-black placeholder:text-[#9e9e9e] transition-colors"
              />
              {fieldErrors.displayName && (
                <p className="mt-1 text-xs text-red-600">{fieldErrors.displayName}</p>
              )}
            </div>

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
              {fieldErrors.email && (
                <p className="mt-1 text-xs text-red-600">{fieldErrors.email}</p>
              )}
            </div>

            <div>
              <label htmlFor="password" className="block text-xs font-medium text-[#37352f] mb-1">
                Password <span className="text-[#9e9e9e] font-normal">(10–72 characters)</span>
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="new-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                className="w-full px-3 py-2 text-sm bg-white border border-black/15 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] text-black placeholder:text-[#9e9e9e] transition-colors"
              />
              {fieldErrors.password && (
                <p className="mt-1 text-xs text-red-600">{fieldErrors.password}</p>
              )}
            </div>

            <button
              type="submit"
              disabled={isRegistering}
              className="w-full mt-2 py-2 px-4 border border-transparent rounded-lg shadow-xs text-sm font-medium text-white bg-black hover:bg-[#2e2d2b] focus:outline-hidden focus:ring-2 focus:ring-offset-2 focus:ring-black disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {isRegistering ? 'Creating account...' : 'Create account'}
            </button>
          </form>

          <div className="mt-6 pt-5 border-t border-black/8 text-center">
            <p className="text-xs text-[#757575]">
              Already have an account?{' '}
              <Link
                to={`/login${next !== '/' ? `?next=${encodeURIComponent(next)}` : ''}`}
                className="font-medium text-[#0075de] hover:underline"
              >
                Sign in
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

