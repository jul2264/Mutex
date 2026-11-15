import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

interface RequireAuthProps {
  children?: React.ReactNode;
}

export const RequireAuth: React.FC<RequireAuthProps> = ({ children }) => {
  const { user, isLoading } = useAuth();
  const location = useLocation();
  const [isDelayed, setIsDelayed] = React.useState(false);

  React.useEffect(() => {
    if (!isLoading) {
      setIsDelayed(false);
      return;
    }
    const timer = setTimeout(() => setIsDelayed(true), 2500);
    return () => clearTimeout(timer);
  }, [isLoading]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#f6f5f4] dark:bg-[#09090b] text-black dark:text-white flex items-center justify-center transition-colors">
        <div className="flex flex-col items-center gap-3 px-4 text-center">
          <div className="w-8 h-8 rounded-full border-2 border-black/10 dark:border-white/10 border-t-black dark:border-t-white animate-spin" />
          <span className="text-xs font-medium text-[#37352f] dark:text-[#e4e4e7]">Authenticating...</span>
          {isDelayed && (
            <p className="text-xs text-[#757575] dark:text-[#a1a1aa] max-w-xs animate-pulse">
              Waking up cloud server (Render free-tier cold start). This may take up to 45 seconds on first visit...
            </p>
          )}
        </div>
      </div>
    );
  }

  if (!user) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }

  return children ? <>{children}</> : null;
};

