import { BrowserRouter } from 'react-router-dom';
import { AppThemeProvider } from './theme/ThemeContext';
import { ToastProvider } from './context/ToastContext';
import { AuthProvider } from './context/AuthContext';
import { AppRoutes } from './routes/AppRoutes';
import { ErrorBoundary } from './components/common/ErrorBoundary';

/**
 * Application root.
 *
 * <p>The outermost error boundary sits inside the theme provider so that, if anything below it throws
 * during render, the fallback UI is still themed and readable rather than unstyled. This is the last line
 * of defence against a blank page: React unmounts the whole tree on an uncaught render error, and without
 * a boundary the user is left with an empty document and no way to recover.
 */
function App() {
  return (
    <AppThemeProvider>
      <ErrorBoundary boundaryName="application root">
        <ToastProvider>
          <BrowserRouter>
            <AuthProvider>
              <AppRoutes />
            </AuthProvider>
          </BrowserRouter>
        </ToastProvider>
      </ErrorBoundary>
    </AppThemeProvider>
  );
}

export default App;
