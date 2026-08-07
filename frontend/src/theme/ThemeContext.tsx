import React, { createContext, useContext, useEffect, useState, useMemo } from 'react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';

type ThemeMode = 'dark' | 'light';

interface ThemeContextType {
  mode: ThemeMode;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType>({
  mode: 'dark',
  toggleTheme: () => {},
});

export const useAppTheme = () => useContext(ThemeContext);

export const AppThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [mode, setMode] = useState<ThemeMode>(() => {
    const saved = localStorage.getItem('qpilot_theme_mode');
    return saved === 'light' ? 'light' : 'dark';
  });

  useEffect(() => {
    localStorage.setItem('qpilot_theme_mode', mode);
    if (mode === 'dark') {
      document.documentElement.classList.add('dark-mode');
      document.documentElement.classList.remove('light-mode');
    } else {
      document.documentElement.classList.add('light-mode');
      document.documentElement.classList.remove('dark-mode');
    }
  }, [mode]);

  const toggleTheme = () => {
    setMode((prev) => (prev === 'dark' ? 'light' : 'dark'));
  };

  const theme = useMemo(() => {
    const isDark = mode === 'dark';
    return createTheme({
      palette: {
        mode,
        primary: {
          main: isDark ? '#10B981' : '#059669',     // Modern Cyber Emerald
          light: '#34D399',
          dark: '#047857',
          contrastText: '#FFFFFF',
        },
        secondary: {
          main: isDark ? '#8B5CF6' : '#7C3AED',   // Modern Deep Violet Accent
          light: '#A78BFA',
          dark: '#6D28D9',
          contrastText: '#FFFFFF',
        },
        background: {
          default: isDark ? '#09090B' : '#F8FAFC', // Raycast/Obsidian Slate
          paper: isDark ? '#121215' : '#FFFFFF',   // Card Surface
        },
        text: {
          primary: isDark ? '#F4F4F5' : '#0F172A',
          secondary: isDark ? '#A1A1AA' : '#64748B',
          disabled: isDark ? '#52525B' : '#94A3B8',
        },
        divider: isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.08)',
        error: { main: '#EF4444', light: '#FCA5A5', dark: '#B91C1C' },
        warning: { main: '#F59E0B', light: '#FCD34D', dark: '#B45309' },
        info: { main: '#3B82F6', light: '#93C5FD', dark: '#1D4ED8' },
        success: { main: '#10B981', light: '#6EE7B7', dark: '#047857' },
        action: {
          hover: isDark ? 'rgba(255, 255, 255, 0.04)' : 'rgba(0, 0, 0, 0.03)',
          selected: isDark ? 'rgba(16, 185, 129, 0.12)' : 'rgba(5, 150, 105, 0.08)',
          disabled: isDark ? 'rgba(255, 255, 255, 0.2)' : 'rgba(0, 0, 0, 0.26)',
        },
      },
      shape: {
        borderRadius: 10,
      },
      typography: {
        fontFamily: '"Plus Jakarta Sans", "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        h1: { fontWeight: 800, letterSpacing: '-0.025em' },
        h2: { fontWeight: 800, letterSpacing: '-0.02em' },
        h3: { fontWeight: 800, letterSpacing: '-0.015em' },
        h4: { fontWeight: 800, letterSpacing: '-0.01em' },
        h5: { fontWeight: 700, letterSpacing: '-0.005em' },
        h6: { fontWeight: 700 },
        subtitle1: { fontWeight: 600 },
        subtitle2: { fontWeight: 600 },
        body1: { fontSize: '0.9375rem', lineHeight: 1.6 },
        body2: { fontSize: '0.84375rem', lineHeight: 1.5 },
        button: { textTransform: 'none', fontWeight: 700, letterSpacing: '0.01em' },
      },
      components: {
        MuiButton: {
          styleOverrides: {
            root: ({ ownerState }) => ({
              borderRadius: 8,
              padding: '8px 18px',
              boxShadow: 'none',
              transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
              ...(ownerState?.variant === 'contained' && ownerState?.color === 'primary' && {
                background: isDark
                  ? 'linear-gradient(135deg, #10B981 0%, #059669 100%)'
                  : 'linear-gradient(135deg, #059669 0%, #047857 100%)',
              }),
              '&:hover': {
                transform: 'translateY(-1px)',
                boxShadow: isDark
                  ? '0 4px 20px 0 rgba(16, 185, 129, 0.25)'
                  : '0 4px 16px 0 rgba(5, 150, 105, 0.2)',
              },
              '&:active': {
                transform: 'translateY(0)',
              },
            }),
          },
        },
        MuiPaper: {
          styleOverrides: {
            root: {
              backgroundImage: 'none',
              borderColor: isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(226, 232, 240, 0.9)',
              transition: 'all 0.2s ease-in-out',
            },
          },
        },
        MuiCard: {
          styleOverrides: {
            root: {
              backgroundImage: 'none',
              borderRadius: 14,
              border: '1px solid',
              borderColor: isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(226, 232, 240, 0.8)',
              backgroundColor: isDark ? '#121215' : '#FFFFFF',
              boxShadow: isDark
                ? '0 10px 30px -10px rgba(0, 0, 0, 0.5)'
                : '0 4px 20px -4px rgba(0, 0, 0, 0.05)',
            },
          },
        },
        MuiChip: {
          styleOverrides: {
            root: {
              borderRadius: 6,
              fontWeight: 700,
            },
          },
        },
        MuiTableCell: {
          styleOverrides: {
            root: {
              borderBottom: '1px solid',
              borderColor: isDark ? 'rgba(255, 255, 255, 0.06)' : 'rgba(0, 0, 0, 0.06)',
              padding: '12px 16px',
            },
            head: {
              fontWeight: 700,
              fontSize: '0.75rem',
              letterSpacing: '0.04em',
              textTransform: 'uppercase',
              backgroundColor: isDark ? '#18181B' : '#F1F5F9',
              color: isDark ? '#A1A1AA' : '#64748B',
            },
          },
        },
        MuiTextField: {
          styleOverrides: {
            root: {
              '& .MuiOutlinedInput-root': {
                borderRadius: 8,
                transition: 'all 0.2s ease',
                '&:hover fieldset': {
                  borderColor: isDark ? '#34D399' : '#059669',
                },
                '&.Mui-focused fieldset': {
                  borderColor: isDark ? '#10B981' : '#059669',
                  borderWidth: 2,
                },
              },
            },
          },
        },
        MuiDialog: {
          styleOverrides: {
            paper: {
              borderRadius: 16,
              border: '1px solid',
              borderColor: isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)',
              backgroundColor: isDark ? '#121215' : '#FFFFFF',
              boxShadow: isDark ? '0 25px 50px -12px rgba(0, 0, 0, 0.8)' : '0 20px 40px -15px rgba(0,0,0,0.1)',
            },
          },
        },
      },
    });
  }, [mode]);

  return (
    <ThemeContext.Provider value={{ mode, toggleTheme }}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeContext.Provider>
  );
};
