import React from 'react';
import { Link } from 'react-router-dom';
import { useTheme } from '../../hooks/useTheme';

interface LogoProps {
  className?: string;
  showText?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

export const Logo: React.FC<LogoProps> = ({ className = '', showText = true, size = 'md' }) => {
  const { theme } = useTheme();

  const sizeStyles = {
    sm: 'w-8 h-8 rounded-lg',
    md: 'w-10 h-10 rounded-xl',
    lg: 'w-12 h-12 rounded-xl',
  }[size];

  const logoSrc = theme === 'dark' ? '/logo-dark.png' : '/logo-light.png';

  return (
    <Link
      to="/"
      className={`font-bold text-2xl tracking-tight text-black dark:text-white flex items-center gap-3 hover:opacity-90 transition-opacity ${className}`}
    >
      <div className={`${sizeStyles} overflow-hidden shadow-xs flex items-center justify-center bg-white dark:bg-black border border-black/8 dark:border-white/10 shrink-0`}>
        <img
          src={logoSrc}
          alt="Mutex"
          className="w-full h-full object-contain p-0.5"
        />
      </div>
      {showText && (
        <span className="font-bold tracking-tight text-black dark:text-white">
          Mutex
        </span>
      )}
    </Link>
  );
};
