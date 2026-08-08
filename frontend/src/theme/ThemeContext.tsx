import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { ThemeProvider, createTheme, type Theme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { brand, darkSurfaces, lightSurfaces, status } from './palette';

type ThemeMode = 'dark' | 'light';

interface ThemeContextValue {
  mode: ThemeMode;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextValue>({
  mode: 'dark',
  toggleTheme: () => {},
});

export const useAppTheme = () => useContext(ThemeContext);

const STORAGE_KEY = 'qpilot.theme';

/**
 * True when the user has asked their OS to reduce motion.
 *
 * <p>Read once and used to strip transition durations from the theme, so honouring the preference is
 * structural rather than something each component has to remember. Guarded for SSR-less safety in case
 * `matchMedia` is unavailable.
 */
function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function buildTheme(mode: ThemeMode, reduceMotion: boolean): Theme {
  const isDark = mode === 'dark';
  const surfaces = isDark ? darkSurfaces : lightSurfaces;

  // Collapsing durations to near-zero rather than 0 keeps MUI's transition callbacks firing, which some
  // components rely on to clean up after themselves.
  const duration = reduceMotion
    ? { shortest: 1, shorter: 1, short: 1, standard: 1, complex: 1, enteringScreen: 1, leavingScreen: 1 }
    : { shortest: 150, shorter: 200, short: 250, standard: 300, complex: 375, enteringScreen: 225, leavingScreen: 195 };

  const motion = (property: string, ms = 200) =>
    reduceMotion ? 'none' : `${property} ${ms}ms cubic-bezier(0.4, 0, 0.2, 1)`;

  return createTheme({
    palette: {
      mode,
      primary: {
        main: brand.primary,
        dark: brand.primaryDark,
        light: brand.primaryLight,
        contrastText: '#FFFFFF',
      },
      secondary: {
        main: brand.secondary,
        dark: brand.secondaryDark,
        light: brand.secondaryLight,
        contrastText: '#04222A',
      },
      background: { default: surfaces.background, paper: surfaces.paper },
      text: {
        primary: surfaces.textPrimary,
        secondary: surfaces.textSecondary,
        disabled: surfaces.textDisabled,
      },
      divider: surfaces.border,
      success: { main: status.success, light: status.successText },
      warning: { main: status.warning, light: status.warningText },
      error: { main: status.error, light: status.errorText },
      info: { main: status.info, light: status.infoText },
      action: {
        hover: surfaces.hover,
        selected: surfaces.selected,
        disabled: isDark ? 'rgba(255,255,255,0.24)' : 'rgba(17,20,34,0.26)',
      },
    },
    shape: { borderRadius: 12 },
    transitions: { duration },
    typography: {
      fontFamily: '"Plus Jakarta Sans", "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      h1: { fontWeight: 800, letterSpacing: '-0.03em' },
      h2: { fontWeight: 800, letterSpacing: '-0.025em' },
      h3: { fontWeight: 800, letterSpacing: '-0.02em' },
      h4: { fontWeight: 800, letterSpacing: '-0.018em' },
      h5: { fontWeight: 750, letterSpacing: '-0.012em' },
      h6: { fontWeight: 700, letterSpacing: '-0.008em' },
      subtitle1: { fontWeight: 650 },
      subtitle2: { fontWeight: 650, letterSpacing: '0.005em' },
      body1: { fontSize: '0.9375rem', lineHeight: 1.65 },
      body2: { fontSize: '0.855rem', lineHeight: 1.6 },
      caption: { fontSize: '0.75rem', lineHeight: 1.5 },
      overline: { fontWeight: 750, letterSpacing: '0.09em', fontSize: '0.68rem' },
      button: { textTransform: 'none', fontWeight: 700, letterSpacing: '0.005em' },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          // Exposed as custom properties so plain CSS (index.css) and inline styles share one source
          // of truth with the MUI theme instead of hardcoding a second copy of the palette.
          ':root': {
            '--qp-bg': surfaces.background,
            '--qp-paper': surfaces.paper,
            '--qp-elevated': surfaces.elevated,
            '--qp-border': surfaces.border,
            '--qp-border-strong': surfaces.borderStrong,
            '--qp-text': surfaces.textPrimary,
            '--qp-text-muted': surfaces.textSecondary,
            '--qp-primary': brand.primary,
            '--qp-secondary': brand.secondary,
            '--qp-accent': brand.accent,
            '--qp-overlay': surfaces.overlay,
            '--qp-motion': reduceMotion ? '0ms' : '220ms',
          },
          // Visible, consistent keyboard focus. The browser default is easy to lose against a dark
          // surface, and removing it outright (a common "fix") makes the app unusable by keyboard.
          '*:focus-visible': {
            outline: `2px solid ${brand.primary}`,
            outlineOffset: '2px',
            borderRadius: '6px',
          },
        },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          // Variant-specific styling is applied from `root` via ownerState rather than through
          // per-variant slot keys, which this MUI major no longer exposes in its override types.
          root: ({ ownerState }) => ({
            borderRadius: 10,
            padding: '8px 18px',
            transition: motion('background-color, box-shadow, transform, border-color'),
            '&:active': { transform: reduceMotion ? 'none' : 'translateY(1px)' },
            ...(ownerState?.variant === 'contained' && ownerState?.color === 'primary'
              ? {
                  background: `linear-gradient(135deg, ${brand.primary} 0%, ${brand.primaryDark} 100%)`,
                  boxShadow: '0 1px 0 0 rgba(255,255,255,0.08) inset',
                  '&:hover': {
                    background: `linear-gradient(135deg, ${brand.primaryLight} 0%, ${brand.primary} 100%)`,
                    boxShadow: `0 6px 22px -8px ${brand.primary}`,
                  },
                }
              : {}),
            ...(ownerState?.variant === 'outlined'
              ? {
                  borderColor: surfaces.borderStrong,
                  '&:hover': { borderColor: brand.primary, backgroundColor: surfaces.selected },
                }
              : {}),
          }),
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: { backgroundImage: 'none' },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
            borderRadius: 16,
            border: `1px solid ${surfaces.border}`,
            backgroundColor: surfaces.paper,
            boxShadow: isDark
              ? '0 1px 2px rgba(0,0,0,0.4), 0 12px 32px -20px rgba(0,0,0,0.9)'
              : '0 1px 2px rgba(17,20,34,0.04), 0 12px 28px -20px rgba(17,20,34,0.22)',
            transition: motion('border-color, box-shadow, transform'),
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: { borderRadius: 7, fontWeight: 700, fontSize: '0.74rem' },
          sizeSmall: { height: 22 },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          root: {
            borderBottom: `1px solid ${surfaces.border}`,
            padding: '11px 14px',
            fontSize: '0.855rem',
          },
          head: {
            fontWeight: 750,
            fontSize: '0.71rem',
            letterSpacing: '0.07em',
            textTransform: 'uppercase',
            backgroundColor: isDark ? darkSurfaces.elevated : '#F1F3F9',
            color: surfaces.textSecondary,
            whiteSpace: 'nowrap',
          },
        },
      },
      MuiTableRow: {
        styleOverrides: {
          root: { transition: motion('background-color', 140) },
        },
      },
      MuiTextField: {
        defaultProps: { size: 'small' },
        styleOverrides: {
          root: {
            '& .MuiOutlinedInput-root': {
              borderRadius: 10,
              transition: motion('border-color, box-shadow'),
              '& fieldset': { borderColor: surfaces.border },
              '&:hover fieldset': { borderColor: surfaces.borderStrong },
              '&.Mui-focused fieldset': { borderColor: brand.primary, borderWidth: 2 },
            },
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: { borderRadius: 10 },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: 18,
            border: `1px solid ${surfaces.borderStrong}`,
            backgroundColor: surfaces.paper,
            backgroundImage: 'none',
            boxShadow: '0 32px 80px -28px rgba(0,0,0,0.75)',
          },
        },
      },
      MuiTooltip: {
        defaultProps: { arrow: true },
        styleOverrides: {
          tooltip: {
            backgroundColor: isDark ? '#242835' : '#1B1E2A',
            fontSize: '0.76rem',
            lineHeight: 1.5,
            padding: '8px 11px',
            borderRadius: 8,
            maxWidth: 340,
          },
        },
      },
      MuiTabs: {
        styleOverrides: {
          root: { minHeight: 42 },
          indicator: { height: 2.5, borderRadius: 2 },
        },
      },
      MuiTab: {
        styleOverrides: {
          root: {
            minHeight: 42,
            fontWeight: 650,
            fontSize: '0.85rem',
            transition: motion('color, background-color', 160),
            '&.Mui-selected': { fontWeight: 750 },
          },
        },
      },
      MuiLinearProgress: {
        styleOverrides: {
          root: { borderRadius: 999, height: 6, backgroundColor: surfaces.hover },
          bar: { borderRadius: 999 },
        },
      },
      MuiAlert: {
        styleOverrides: {
          root: { borderRadius: 12, alignItems: 'flex-start' },
        },
      },
      MuiAccordion: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
            '&:before': { display: 'none' },
          },
        },
      },
    },
  });
}

export const AppThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [mode, setMode] = useState<ThemeMode>(() => {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'light' || saved === 'dark') {
      return saved;
    }
    // No stored choice: follow the OS rather than forcing dark on someone in a bright room.
    return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: light)').matches
      ? 'light'
      : 'dark';
  });

  const [reduceMotion, setReduceMotion] = useState(prefersReducedMotion);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, mode);
    document.documentElement.dataset.theme = mode;
  }, [mode]);

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') {
      return;
    }
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = (event: MediaQueryListEvent) => setReduceMotion(event.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  const toggleTheme = useCallback(() => {
    setMode((previous) => (previous === 'dark' ? 'light' : 'dark'));
  }, []);

  const theme = useMemo(() => buildTheme(mode, reduceMotion), [mode, reduceMotion]);
  const value = useMemo(() => ({ mode, toggleTheme }), [mode, toggleTheme]);

  return (
    <ThemeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeContext.Provider>
  );
};
