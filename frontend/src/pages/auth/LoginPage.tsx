import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  IconButton,
  InputAdornment,
  Link,
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
  ArrowRight,
  ShieldCheck,
  Zap,
  CheckCircle2,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { extractErrorMessage } from '../../api/httpClient';
import type { LoginRequest } from '../../types/auth';

export function LoginPage() {
  const { login } = useAuth();
  const { showSuccess } = useToast();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<LoginRequest>();

  async function onSubmit(values: LoginRequest) {
    setServerError(null);
    setIsSubmitting(true);
    try {
      await login(values);
      showSuccess('Welcome back to QPilot AI!');
      navigate('/dashboard', { replace: true });
    } catch (error) {
      setServerError(extractErrorMessage(error, 'Invalid email or password.'));
    } finally {
      setIsSubmitting(false);
    }
  }

  const handleFillDemo = (email: string) => {
    setValue('email', email);
    setValue('password', 'password123');
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(circle at 10% 20%, rgba(99, 102, 241, 0.15) 0%, transparent 40%), radial-gradient(circle at 90% 80%, rgba(16, 185, 129, 0.15) 0%, transparent 40%), #090D16',
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
        {/* Left Feature Hero Side */}
        <Box
          sx={{
            flex: 1,
            p: { xs: 4, md: 5 },
            background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(139, 92, 246, 0.1) 100%)',
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
                  background: 'linear-gradient(135deg, #6366F1 0%, #8B5CF6 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 6px 16px rgba(99, 102, 241, 0.4)',
                }}
              >
                <Bot size={24} color="#FFF" />
              </Box>
              <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '-0.02em' }}>
                QPilot AI
              </Typography>
            </Box>

            <Typography variant="h4" sx={{ fontWeight: 800, mb: 2, lineHeight: 1.2 }}>
              Autonomous AI Quality Engineering Platform
            </Typography>

            <Typography variant="body1" color="text.secondary" sx={{ mb: 4, lineHeight: 1.6 }}>
              Supercharge your test lifecycle. Generate multi-framework unit tests, synthetic web audits, API collections & safe load simulations in seconds.
            </Typography>

            <Stack spacing={2} sx={{ mb: 4 }}>
              {[
                'Multi-Agent Architecture & Codebase RAG Discovery',
                'JUnit, RestAssured, Playwright & Cypress Test Generation',
                'Synthetic Website Auditor (Broken Links, SEO, WCAG)',
                'Safe Load & Performance Stress Test Engine',
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
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
              TEST DRIVE DEMO ACCOUNTS
            </Typography>
            <Stack direction="row" spacing={1}>
              <Chip
                icon={<Zap size={14} />}
                label="Dev Account"
                clickable
                onClick={() => handleFillDemo('dev@testforge.com')}
                color="primary"
                variant="outlined"
                size="small"
              />
              <Chip
                icon={<ShieldCheck size={14} />}
                label="QA Lead Account"
                clickable
                onClick={() => handleFillDemo('qa@testforge.com')}
                color="secondary"
                variant="outlined"
                size="small"
              />
            </Stack>
          </Box>
        </Box>

        {/* Right Form Side */}
        <Box sx={{ flex: 1, p: { xs: 4, md: 5 }, bgcolor: 'background.paper', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
          <Typography variant="h5" sx={{ fontWeight: 700, mb: 0.5 }}>
            Sign In to QPilot
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Enter your credentials to access your testing workspace.
          </Typography>

          {serverError && (
            <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>
              {serverError}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
            <Stack spacing={2.5}>
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
                {...register('email', { required: 'Email address is required' })}
              />

              <TextField
                label="Password"
                type={showPassword ? 'text' : 'password'}
                fullWidth
                autoComplete="current-password"
                error={!!errors.password}
                helperText={errors.password?.message}
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Lock size={18} style={{ opacity: 0.6 }} />
                      </InputAdornment>
                    ),
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={() => setShowPassword(!showPassword)} edge="end">
                          {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                        </IconButton>
                      </InputAdornment>
                    ),
                  },
                }}
                {...register('password', { required: 'Password is required' })}
              />

              <Button
                type="submit"
                variant="contained"
                size="large"
                disabled={isSubmitting}
                endIcon={<ArrowRight size={18} />}
                sx={{ py: 1.4, fontWeight: 700, borderRadius: 2.5 }}
              >
                {isSubmitting ? 'Authenticating…' : 'Sign In to Workspace'}
              </Button>
            </Stack>
          </Box>

          <Typography variant="body2" align="center" sx={{ mt: 4, color: 'text.secondary' }}>
            Don&apos;t have an account?{' '}
            <Link component={RouterLink} to="/register" sx={{ fontWeight: 700, color: 'primary.main', textDecoration: 'none' }}>
              Create Account
            </Link>
          </Typography>
        </Box>
      </Card>
    </Box>
  );
}
