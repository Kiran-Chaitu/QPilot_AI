import { useState } from 'react';
import { Link as RouterLink, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, Divider, Stack, TextField, Typography } from '@mui/material';
import { LogIn } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { extractErrorMessage } from '../../api/httpClient';
import { AuthShell } from './AuthShell';

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login, isAuthenticated } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // The interceptor appends ?expired=1 when it clears a rejected token, so the user is told why they
  // were returned here instead of being silently bounced to the login form.
  const sessionExpired = searchParams.get('expired') === '1';

  // Declarative redirect rather than calling navigate() during render. Navigating mid-render mutates
  // router state while React is rendering, which React flags and which can loop.
  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      await login({ email: email.trim(), password });
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not sign in. Check your email and password.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthShell title="Sign in" subtitle="Access your projects, analyses and test results.">
      <Box component="form" onSubmit={handleSubmit} noValidate>
        <Stack spacing={2}>
          {sessionExpired && (
            <Alert severity="info" variant="outlined" sx={{ borderRadius: 2.5 }}>
              <Typography variant="caption">Your session expired, so you were signed out. Sign in again to continue.</Typography>
            </Alert>
          )}

          {error && (
            <Alert severity="error" variant="outlined" sx={{ borderRadius: 2.5 }}>
              <Typography variant="body2">{error}</Typography>
            </Alert>
          )}

          <TextField
            label="Email"
            type="email"
            fullWidth
            required
            autoComplete="email"
            autoFocus
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            disabled={isSubmitting}
          />
          <TextField
            label="Password"
            type="password"
            fullWidth
            required
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={isSubmitting}
          />

          <Button
            type="submit"
            variant="contained"
            size="large"
            fullWidth
            startIcon={isSubmitting ? <CircularProgress size={16} color="inherit" /> : <LogIn size={17} />}
            disabled={isSubmitting || !email.trim() || !password}
            sx={{ fontWeight: 780, py: 1.15 }}
          >
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </Button>

          <Divider sx={{ my: 0.5 }}>
            <Typography variant="caption" color="text.secondary">
              new here?
            </Typography>
          </Divider>

          <Button component={RouterLink} to="/register" variant="outlined" fullWidth sx={{ fontWeight: 700 }}>
            Create an account
          </Button>

          <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
            Trouble signing in? Confirm the API is reachable at the address configured in{' '}
            <Box component="code" sx={{ fontSize: '0.72rem' }}>
              VITE_API_BASE_URL
            </Box>
            .
          </Typography>
        </Stack>
      </Box>
    </AuthShell>
  );
}
