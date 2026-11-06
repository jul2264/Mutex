import React from 'react';
import { Sun, Moon } from 'lucide-react';
import { useTheme } from '../../hooks/useTheme';

interface ThemeToggleProps {
  className?: string;
}

export const ThemeToggle: React.FC<ThemeToggleProps> = ({ className = '' }) => {
  const { theme, toggleTheme } = useTheme();

  return (
    <button
      type="button"
      onClick={toggleTheme}
      className={`p-2 rounded-xl border border-black/10 dark:border-white/15 bg-white dark:bg-[#18181b] text-[#333] dark:text-[#f4f4f5] hover:bg-[#f6f5f4] dark:hover:bg-[#27272a] hover:border-black/25 dark:hover:border-white/30 transition-all shadow-2xs cursor-pointer flex items-center justify-center ${className}`}
      title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
      aria-label="Toggle light and dark mode"
    >
      {theme === 'dark' ? (
        <Sun className="w-4 h-4 text-amber-400" />
      ) : (
        <Moon className="w-4 h-4 text-[#0075de]" />
      )}
    </button>
  );
};
