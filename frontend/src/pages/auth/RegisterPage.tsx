import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { UserPlus } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { extractErrorMessage } from '../../api/httpClient';
import { AuthShell } from './AuthShell';
import type { UserRole } from '../../types/auth';

const ROLES: Array<{ value: UserRole; label: string; hint: string }> = [
  { value: 'DEVELOPER', label: 'Developer', hint: 'Analyze projects and run tests' },
  { value: 'QA_ENGINEER', label: 'QA engineer', hint: 'Analyze projects and run tests' },
  { value: 'QA_LEAD', label: 'QA lead', hint: 'Analyze projects and run tests' },
  { value: 'ADMIN', label: 'Administrator', hint: 'Also manages the shared AI provider key' },
];

const MIN_PASSWORD_LENGTH = 6;

export function RegisterPage() {
  const navigate = useNavigate();
  const { register } = useAuth();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [role, setRole] = useState<UserRole>('QA_ENGINEER');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Validated client-side so the user gets immediate feedback; the server enforces the same rules
  // independently, since client validation is a convenience and not a guarantee.
  const passwordTooShort = password.length > 0 && password.length < MIN_PASSWORD_LENGTH;
  const passwordsDiffer = confirmPassword.length > 0 && password !== confirmPassword;
  const canSubmit =
    fullName.trim().length > 0 &&
    email.trim().length > 0 &&
    password.length >= MIN_PASSWORD_LENGTH &&
    password === confirmPassword &&
    !isSubmitting;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      await register({ fullName: fullName.trim(), email: email.trim(), password, role });
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not create the account.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthShell title="Create an account" subtitle="Set up a workspace for your projects and test results.">
      <Box component="form" onSubmit={handleSubmit} noValidate>
        <Stack spacing={2}>
          {error && (
            <Alert severity="error" variant="outlined" sx={{ borderRadius: 2.5 }}>
              <Typography variant="body2">{error}</Typography>
            </Alert>
          )}

          <TextField
            label="Full name"
            fullWidth
            required
            autoComplete="name"
            autoFocus
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            disabled={isSubmitting}
          />
          <TextField
            label="Email"
            type="email"
            fullWidth
            required
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            disabled={isSubmitting}
            helperText="Email addresses are stored lowercase and must be unique."
          />

          <FormControl fullWidth size="small">
            <InputLabel id="register-role">Role</InputLabel>
            <Select
              labelId="register-role"
              value={role}
              label="Role"
              onChange={(event) => setRole(event.target.value as UserRole)}
              disabled={isSubmitting}
            >
              {ROLES.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 650 }}>
                      {option.label}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {option.hint}
                    </Typography>
                  </Box>
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <TextField
            label="Password"
            type="password"
            fullWidth
            required
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={isSubmitting}
            error={passwordTooShort}
            helperText={passwordTooShort ? `At least ${MIN_PASSWORD_LENGTH} characters required.` : ' '}
          />
          <TextField
            label="Confirm password"
            type="password"
            fullWidth
            required
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            disabled={isSubmitting}
            error={passwordsDiffer}
            helperText={passwordsDiffer ? 'The two passwords do not match.' : ' '}
          />

          <Button
            type="submit"
            variant="contained"
            size="large"
            fullWidth
            startIcon={isSubmitting ? <CircularProgress size={16} color="inherit" /> : <UserPlus size={17} />}
            disabled={!canSubmit}
            sx={{ fontWeight: 780, py: 1.15 }}
          >
            {isSubmitting ? 'Creating account…' : 'Create account'}
          </Button>

          <Divider sx={{ my: 0.5 }}>
            <Typography variant="caption" color="text.secondary">
              already registered?
            </Typography>
          </Divider>

          <Button component={RouterLink} to="/login" variant="outlined" fullWidth sx={{ fontWeight: 700 }}>
            Sign in instead
          </Button>
        </Stack>
      </Box>
    </AuthShell>
  );
}
