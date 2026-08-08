import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { DashboardPage } from '../pages/dashboard/DashboardPage';
import { ProjectDetailPage } from '../pages/project/ProjectDetailPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { ProtectedRoute } from '../components/common/ProtectedRoute';
import { ErrorBoundary } from '../components/common/ErrorBoundary';

/**
 * Application routes.
 *
 * <p>Two deliberate choices here. Unknown paths render a real 404 instead of silently redirecting to the
 * dashboard — a redirect hides the fact that the URL was wrong, so a stale bookmark appears to work while
 * quietly landing somewhere else. And each routed page is wrapped in an error boundary, so a render
 * exception inside one page cannot blank the entire application.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <ErrorBoundary boundaryName="dashboard">
              <DashboardPage />
            </ErrorBoundary>
          </ProtectedRoute>
        }
      />
      <Route
        path="/projects/:id"
        element={
          <ProtectedRoute>
            <ErrorBoundary boundaryName="project detail">
              <ProjectDetailPage />
            </ErrorBoundary>
          </ProtectedRoute>
        }
      />

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
