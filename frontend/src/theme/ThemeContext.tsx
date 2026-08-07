import React, { createContext, useContext, useEffect, useState } from 'react';
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

  const theme = React.useMemo(() => {
    const isDark = mode === 'dark';
    return createTheme({
      palette: {
        mode,
        primary: {
          main: isDark ? '#10B981' : '#059669', // Cyber Emerald Mint accent
          light: '#34D399',
          dark: '#047857',
          contrastText: '#FFFFFF',
        },
        secondary: {
          main: isDark ? '#A855F7' : '#9333EA', // Electric Violet
          light: '#C084FC',
          dark: '#7E22CE',
          contrastText: '#FFFFFF',
        },
        background: {
          default: isDark ? '#09090B' : '#F8FAFC', // Pure Obsidian Pitch Black
          paper: isDark ? '#121215' : '#FFFFFF',   // Dark Obsidian Onyx Card Surface
        },
        text: {
          primary: isDark ? '#FAFAFA' : '#0F172A',
          secondary: isDark ? '#A1A1AA' : '#475569',
        },
        divider: isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.08)',
        error: { main: '#EF4444' },
        warning: { main: '#F59E0B' },
        info: { main: '#3B82F6' },
        success: { main: '#10B981' },
      },
      typography: {
        fontFamily: '"Plus Jakarta Sans", "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        h1: { fontWeight: 800, letterSpacing: '-0.02em' },
        h2: { fontWeight: 800, letterSpacing: '-0.02em' },
        h3: { fontWeight: 800, letterSpacing: '-0.01em' },
        h4: { fontWeight: 800, letterSpacing: '-0.01em' },
        h5: { fontWeight: 700 },
        h6: { fontWeight: 700 },
        subtitle1: { fontWeight: 600 },
        button: { textTransform: 'none', fontWeight: 800 },
      },
      shape: {
        borderRadius: 12,
      },
      components: {
        MuiButton: {
          styleOverrides: {
            root: {
              borderRadius: 10,
              padding: '9px 20px',
              boxShadow: 'none',
              transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
              '&:hover': {
                transform: 'translateY(-2px)',
                boxShadow: isDark
                  ? '0 6px 24px 0 rgba(16, 185, 129, 0.25)'
                  : '0 6px 20px 0 rgba(5, 150, 105, 0.2)',
              },
            },
          },
        },
        MuiPaper: {
          styleOverrides: {
            root: {
              backgroundImage: 'none',
              borderColor: isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.08)',
              transition: 'all 0.2s ease-in-out',
            },
          },
        },
        MuiCard: {
          styleOverrides: {
            root: {
              backgroundImage: 'none',
              borderRadius: 16,
              border: '1px solid',
              borderColor: isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(226, 232, 240, 0.8)',
              backgroundColor: isDark ? '#121215' : '#FFFFFF',
              boxShadow: isDark ? '0 12px 40px rgba(0,0,0,0.6)' : '0 4px 20px rgba(0,0,0,0.05)',
            },
          },
        },
        MuiChip: {
          styleOverrides: {
            root: {
              borderRadius: 8,
              fontWeight: 700,
            },
          },
        },
        MuiTableCell: {
          styleOverrides: {
            root: {
              borderBottom: '1px solid',
              borderColor: isDark ? 'rgba(255, 255, 255, 0.06)' : 'rgba(0, 0, 0, 0.06)',
            },
            head: {
              fontWeight: 800,
              backgroundColor: isDark ? '#18181B' : '#F1F5F9',
              color: isDark ? '#34D399' : '#047857',
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
