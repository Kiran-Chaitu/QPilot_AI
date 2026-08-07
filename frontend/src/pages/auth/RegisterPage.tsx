import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Link,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import { useAuth } from '../../context/AuthContext';
import { extractErrorMessage } from '../../api/httpClient';
import type { RegisterRequest, UserRole } from '../../types/auth';

const ROLE_OPTIONS: { value: UserRole; label: string }[] = [
  { value: 'DEVELOPER', label: 'Developer' },
  { value: 'QA_ENGINEER', label: 'QA Engineer' },
  { value: 'ADMIN', label: 'Admin' },
];

export function RegisterPage() {
  const { register: registerUser } = useAuth();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterRequest>({ defaultValues: { role: 'DEVELOPER' } });

  async function onSubmit(values: RegisterRequest) {
    setServerError(null);
    setIsSubmitting(true);
    try {
      await registerUser(values);
      navigate('/dashboard', { replace: true });
    } catch (error) {
      setServerError(extractErrorMessage(error, 'Could not create your account.'));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #1e3c72 0%, #2a7f62 100%)',
        p: 2,
      }}
    >
      <Paper elevation={6} sx={{ p: 5, width: '100%', maxWidth: 460, borderRadius: 3 }}>
        <Stack spacing={1} sx={{ mb: 3, alignItems: 'center' }}>
          <SmartToyIcon color="primary" sx={{ fontSize: 40 }} />
          <Typography variant="h5">Create your account</Typography>
          <Typography variant="body2" color="text.secondary" align="center">
            Join AI TestPilot and let AI agents handle your QA.
          </Typography>
        </Stack>

        {serverError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {serverError}
          </Alert>
        )}

        <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
          <Stack spacing={2.5}>
            <TextField
              label="Full name"
              fullWidth
              autoComplete="name"
              error={!!errors.fullName}
              helperText={errors.fullName?.message}
              {...register('fullName', { required: 'Full name is required' })}
            />
            <TextField
              label="Email"
              type="email"
              fullWidth
              autoComplete="email"
              error={!!errors.email}
              helperText={errors.email?.message}
              {...register('email', { required: 'Email is required' })}
            />
            <TextField
              label="Password"
              type="password"
              fullWidth
              autoComplete="new-password"
              error={!!errors.password}
              helperText={errors.password?.message ?? 'At least 6 characters'}
              {...register('password', {
                required: 'Password is required',
                minLength: { value: 6, message: 'Password must be at least 6 characters' },
              })}
            />
            <TextField select label="Role" fullWidth defaultValue="DEVELOPER" {...register('role')}>
              {ROLE_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>
            <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
              {isSubmitting ? 'Creating account…' : 'Create Account'}
            </Button>
          </Stack>
        </Box>

        <Typography variant="body2" align="center" sx={{ mt: 3 }}>
          Already have an account? <Link component={RouterLink} to="/login">Sign in</Link>
        </Typography>
      </Paper>
    </Box>
  );
}
