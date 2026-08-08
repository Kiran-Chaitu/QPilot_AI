import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  IconButton,
  InputAdornment,
  Link,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import {
  Bot,
  Eye,
  EyeOff,
  Mail,
  Lock,
  User,
  ShieldCheck,
  ArrowRight,
  CheckCircle2,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { extractErrorMessage } from '../../api/httpClient';
import type { RegisterRequest, UserRole } from '../../types/auth';

const ROLE_OPTIONS: { value: UserRole; label: string; desc: string }[] = [
  { value: 'DEVELOPER', label: 'Developer', desc: 'Generate unit, integration & API tests' },
  { value: 'QA_ENGINEER', label: 'QA Engineer', desc: 'Perform full quality & security analysis' },
  { value: 'ADMIN', label: 'Admin / Team Lead', desc: 'Full workspace & team access' },
];

export function RegisterPage() {
  const { register: registerUser } = useAuth();
  const { showSuccess } = useToast();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

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
      showSuccess('Account created successfully! Welcome to QPilot.');
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
        background: 'radial-gradient(circle at 90% 20%, rgba(139, 92, 246, 0.15) 0%, transparent 40%), radial-gradient(circle at 10% 80%, rgba(99, 102, 241, 0.15) 0%, transparent 40%), #090D16',
        p: 2,
      }}
    >
      <Card
        elevation={0}
        sx={{
          display: 'flex',
          flexDirection: { xs: 'column', md: 'row' },
          width: '100%',
          maxWidth: 960,
          borderRadius: 4,
          overflow: 'hidden',
          border: '1px solid',
          borderColor: 'divider',
          boxShadow: '0 25px 60px rgba(0, 0, 0, 0.6)',
        }}
      >
        {/* Left Hero Side */}
        <Box
          sx={{
            flex: 1,
            p: { xs: 4, md: 5 },
            background: 'linear-gradient(135deg, rgba(139, 92, 246, 0.2) 0%, rgba(99, 102, 241, 0.1) 100%)',
            borderRight: { md: '1px solid' },
            borderColor: 'divider',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
          }}
        >
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 4 }}>
              <Box
                sx={{
                  width: 42,
                  height: 42,
                  borderRadius: '12px',
                  background: 'linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 6px 16px rgba(139, 92, 246, 0.4)',
                }}
              >
                <Bot size={24} color="#FFF" />
              </Box>
              <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '-0.02em' }}>
                QPilot AI
              </Typography>
            </Box>

            <Typography variant="h4" sx={{ fontWeight: 800, mb: 2, lineHeight: 1.2 }}>
              Get Started with Enterprise AI QA
            </Typography>

            <Typography variant="body1" color="text.secondary" sx={{ mb: 4, lineHeight: 1.6 }}>
              Join thousands of software engineers who automate unit tests, integration flows, and security audits with QPilot.
            </Typography>

            <Stack spacing={2} sx={{ mb: 4 }}>
              {[
                'Instant Project & Swagger Parsing',
                'Comprehensive Risk & Coverage Scoring',
                'PDF & Markdown Executive Reports',
                'Continuous Quality Engineering Pipeline',
              ].map((feat) => (
                <Box key={feat} sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <CheckCircle2 size={18} color="#10B981" />
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {feat}
                  </Typography>
                </Box>
              ))}
            </Stack>
          </Box>

          <Box sx={{ pt: 2, borderTop: '1px solid rgba(255, 255, 255, 0.1)' }}>
            <Typography variant="caption" color="text.secondary">
              🔒 Enterprise Security Guaranteed • SOC2 Ready Architecture
            </Typography>
          </Box>
        </Box>

        {/* Right Form Side */}
        <Box sx={{ flex: 1, p: { xs: 4, md: 5 }, bgcolor: 'background.paper', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
          <Typography variant="h5" sx={{ fontWeight: 700, mb: 0.5 }}>
            Create Account
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Set up your user account to access the AI quality platform.
          </Typography>

          {serverError && (
            <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>
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
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <User size={18} style={{ opacity: 0.6 }} />
                      </InputAdornment>
                    ),
                  },
                }}
                {...register('fullName', { required: 'Full name is required' })}
              />

              <TextField
                label="Email address"
                type="email"
                fullWidth
                autoComplete="email"
                error={!!errors.email}
                helperText={errors.email?.message}
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Mail size={18} style={{ opacity: 0.6 }} />
                      </InputAdornment>
                    ),
                  },
                }}
                {...register('email', { required: 'Email is required' })}
              />

              <TextField
                label="Password"
                type={showPassword ? 'text' : 'password'}
                fullWidth
                autoComplete="new-password"
                error={!!errors.password}
                helperText={errors.password?.message ?? 'At least 6 characters'}
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Lock size={18} style={{ opacity: 0.6 }} />
                      </InputAdornment>
                    ),
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          type="button"
                          onClick={() => setShowPassword((prev) => !prev)}
                          edge="end"
                          aria-label={showPassword ? 'Hide password' : 'Show password'}
                          tabIndex={-1}
                        >
                          {showPassword ? <EyeOff size={18} color="#6366F1" /> : <Eye size={18} />}
                        </IconButton>
                      </InputAdornment>
                    ),
                  },
                }}
                {...register('password', {
                  required: 'Password is required',
                  minLength: { value: 6, message: 'Password must be at least 6 characters' },
                })}
              />

              <TextField
                select
                label="Primary Role"
                fullWidth
                defaultValue="DEVELOPER"
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <ShieldCheck size={18} style={{ opacity: 0.6 }} />
                      </InputAdornment>
                    ),
                  },
                }}
                {...register('role')}
              >
                {ROLE_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    <Box>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {option.label}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {option.desc}
                      </Typography>
                    </Box>
                  </MenuItem>
                ))}
              </TextField>

              <Button
                type="submit"
                variant="contained"
                size="large"
                disabled={isSubmitting}
                endIcon={<ArrowRight size={18} />}
                sx={{ py: 1.4, fontWeight: 700, borderRadius: 2.5 }}
              >
                {isSubmitting ? 'Creating Account…' : 'Complete Registration'}
              </Button>
            </Stack>
          </Box>

          <Typography variant="body2" align="center" sx={{ mt: 4, color: 'text.secondary' }}>
            Already have an account?{' '}
            <Link component={RouterLink} to="/login" sx={{ fontWeight: 700, color: 'primary.main', textDecoration: 'none' }}>
              Sign In
            </Link>
          </Typography>
        </Box>
      </Card>
    </Box>
  );
}
