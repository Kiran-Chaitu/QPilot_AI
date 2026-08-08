import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Collapse,
  Grid,
  IconButton,
  LinearProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Play,
  MonitorCheck,
  ShieldCheck,
  Copy,
  Download,
  Check,
  X,
  Timer,
  ChevronDown,
  ChevronUp,
  Lock,
  Globe,
  Sparkles,
} from 'lucide-react';
import { runE2eTest, type E2eTestResponse } from '../../../api/e2eTestApi';
import { useToast } from '../../../context/ToastContext';

function getCategoryColor(category: string) {
  switch (category) {
    case 'CONNECTIVITY': return '#10B981';
    case 'AUTHENTICATION': return '#6366F1';
    case 'NAVIGATION': return '#3B82F6';
    case 'API_HEALTH': return '#F59E0B';
    case 'SECURITY': return '#EF4444';
    default: return '#A1A1AA';
  }
}

function getCategoryIcon(category: string) {
  switch (category) {
    case 'CONNECTIVITY': return <Globe size={14} />;
    case 'AUTHENTICATION': return <Lock size={14} />;
    case 'NAVIGATION': return <MonitorCheck size={14} />;
    case 'API_HEALTH': return <Timer size={14} />;
    case 'SECURITY': return <ShieldCheck size={14} />;
    default: return <Sparkles size={14} />;
  }
}

export function E2eTestTab({ defaultUrl }: { defaultUrl?: string }) {
  const { showSuccess, showError } = useToast();
  const [targetUrl, setTargetUrl] = useState(defaultUrl || '');
  const [loginUrl, setLoginUrl] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showLoginFields, setShowLoginFields] = useState(false);
  const [isRunning, setIsRunning] = useState(false);
  const [result, setResult] = useState<E2eTestResponse | null>(null);
  const [showScript, setShowScript] = useState(false);
  const [copied, setCopied] = useState(false);

  const handleRun = async () => {
    if (!targetUrl) return;
    setIsRunning(true);
    setResult(null);
    try {
      const data = await runE2eTest(
        targetUrl,
        showLoginFields ? loginUrl : undefined,
        showLoginFields ? username : undefined,
        showLoginFields ? password : undefined
      );
      setResult(data);
      showSuccess(`E2E smoke test completed — ${data.passedChecks}/${data.totalChecks} checks passed!`);
    } catch {
      showError('E2E test failed. Please check the target URL and try again.');
    } finally {
      setIsRunning(false);
    }
  };

  const handleCopyScript = async () => {
    if (!result?.generatedPlaywrightScript) return;
    try {
      await navigator.clipboard.writeText(result.generatedPlaywrightScript);
      setCopied(true);
      showSuccess('Playwright script copied to clipboard!');
      setTimeout(() => setCopied(false), 2000);
    } catch {
      showError('Failed to copy to clipboard');
    }
  };

  const handleDownloadScript = () => {
    if (!result?.generatedPlaywrightScript) return;
    const blob = new Blob([result.generatedPlaywrightScript], { type: 'text/typescript' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'e2e-test.spec.ts';
    a.click();
    URL.revokeObjectURL(url);
  };

  const passRate = result ? Math.round((result.passedChecks / result.totalChecks) * 100) : 0;

  return (
    <Stack spacing={3}>
      {/* Header Banner */}
      <Card
        sx={{
          p: 3,
          border: '1px solid rgba(99, 102, 241, 0.3)',
          background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(168, 85, 247, 0.05) 100%)',
          backdropFilter: 'blur(16px)',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
          <MonitorCheck size={24} color="#6366F1" />
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            E2E Browser Smoke Test Engine
          </Typography>
          <Chip label="HTTP-Based Checks" color="primary" size="small" variant="outlined" />
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Run automated end-to-end smoke tests against any URL. Tests include page accessibility, login flow simulation,
          authenticated page access, internal link health, API endpoint checks, and security header validation.
          Generates a downloadable Playwright test script for your CI/CD pipeline.
        </Typography>

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: showLoginFields ? 6 : 8 }}>
            <TextField
              label="Target URL"
              fullWidth
              size="small"
              value={targetUrl}
              onChange={(e) => setTargetUrl(e.target.value)}
              placeholder="https://your-app.com"
              disabled={isRunning}
            />
          </Grid>
          {showLoginFields && (
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                label="Login URL (API endpoint or login page)"
                fullWidth
                size="small"
                value={loginUrl}
                onChange={(e) => setLoginUrl(e.target.value)}
                placeholder="https://your-app.com/api/auth/login"
                disabled={isRunning}
              />
            </Grid>
          )}
          {showLoginFields && (
            <>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  label="Username / Email"
                  fullWidth
                  size="small"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="admin@example.com"
                  disabled={isRunning}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  label="Password"
                  fullWidth
                  size="small"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  disabled={isRunning}
                />
              </Grid>
            </>
          )}
          <Grid size={{ xs: 12, md: showLoginFields ? 4 : 2 }}>
            <Button
              fullWidth
              variant="outlined"
              size="large"
              startIcon={<Lock size={16} />}
              onClick={() => setShowLoginFields(!showLoginFields)}
              disabled={isRunning}
              sx={{ fontWeight: 700, borderRadius: 2, height: '100%' }}
            >
              {showLoginFields ? 'Hide Login' : 'Add Login Flow'}
            </Button>
          </Grid>
          <Grid size={{ xs: 12, md: showLoginFields ? 12 : 2 }}>
            <Button
              fullWidth
              variant="contained"
              color="primary"
              size="large"
              startIcon={isRunning ? <CircularProgress size={16} color="inherit" /> : <Play size={18} />}
              onClick={handleRun}
              disabled={isRunning || !targetUrl}
              sx={{ fontWeight: 700, borderRadius: 2, height: '100%' }}
            >
              {isRunning ? 'Running E2E Tests…' : 'Run E2E Smoke Test'}
            </Button>
          </Grid>
        </Grid>

        {isRunning && (
          <Box sx={{ mt: 3 }}>
            <LinearProgress color="primary" sx={{ height: 6, borderRadius: 3 }} />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              Testing page accessibility, crawling links, checking API health endpoints, validating security headers...
            </Typography>
          </Box>
        )}
      </Card>

      {/* Results View */}
      {result && (
        <>
          {/* Summary Stats */}
          <Grid container spacing={2}>
            <Grid size={{ xs: 6, sm: 3 }}>
              <Paper
                sx={{
                  p: 2.5,
                  textAlign: 'center',
                  borderRadius: 3,
                  border: '1px solid rgba(255, 255, 255, 0.08)',
                  background: 'linear-gradient(145deg, rgba(15, 23, 42, 0.6) 0%, rgba(9, 13, 22, 0.8) 100%)',
                }}
              >
                <Typography variant="h3" sx={{ fontWeight: 800, color: passRate >= 70 ? '#10B981' : passRate >= 40 ? '#F59E0B' : '#EF4444' }}>
                  {passRate}%
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>Pass Rate</Typography>
              </Paper>
            </Grid>
            <Grid size={{ xs: 6, sm: 3 }}>
              <Paper
                sx={{
                  p: 2.5,
                  textAlign: 'center',
                  borderRadius: 3,
                  border: '1px solid rgba(255, 255, 255, 0.08)',
                  background: 'linear-gradient(145deg, rgba(15, 23, 42, 0.6) 0%, rgba(9, 13, 22, 0.8) 100%)',
                }}
              >
                <Typography variant="h3" sx={{ fontWeight: 800, color: '#6366F1' }}>
                  {result.totalChecks}
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>Total Checks</Typography>
              </Paper>
            </Grid>
            <Grid size={{ xs: 6, sm: 3 }}>
              <Paper
                sx={{
                  p: 2.5,
                  textAlign: 'center',
                  borderRadius: 3,
                  border: '1px solid rgba(16, 185, 129, 0.15)',
                  background: 'linear-gradient(145deg, rgba(15, 23, 42, 0.6) 0%, rgba(9, 13, 22, 0.8) 100%)',
                }}
              >
                <Typography variant="h3" sx={{ fontWeight: 800, color: '#10B981' }}>
                  {result.passedChecks}
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>Passed</Typography>
              </Paper>
            </Grid>
            <Grid size={{ xs: 6, sm: 3 }}>
              <Paper
                sx={{
                  p: 2.5,
                  textAlign: 'center',
                  borderRadius: 3,
                  border: result.failedChecks > 0 ? '1px solid rgba(239, 68, 68, 0.15)' : '1px solid rgba(255, 255, 255, 0.08)',
                  background: 'linear-gradient(145deg, rgba(15, 23, 42, 0.6) 0%, rgba(9, 13, 22, 0.8) 100%)',
                }}
              >
                <Typography variant="h3" sx={{ fontWeight: 800, color: result.failedChecks > 0 ? '#EF4444' : '#10B981' }}>
                  {result.failedChecks}
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>Failed</Typography>
              </Paper>
            </Grid>
          </Grid>

          {/* Execution Time */}
          <Paper sx={{ p: 1.5, borderRadius: 2, border: '1px solid rgba(255, 255, 255, 0.06)' }}>
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'center' }}>
              <Timer size={16} color="#A1A1AA" />
              <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                Total Execution Time: <strong style={{ color: '#10B981' }}>{result.executionTimeMs} ms</strong>
              </Typography>
              <Typography variant="body2" color="text.secondary">•</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                Target: <strong style={{ color: '#6366F1' }}>{result.targetUrl}</strong>
              </Typography>
            </Stack>
          </Paper>

          {/* Test Results Table */}
          <Card sx={{ p: 3 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
              <MonitorCheck size={20} color="#6366F1" />
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Test Results
              </Typography>
            </Box>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700, fontSize: '0.75rem' }}>Status</TableCell>
                  <TableCell sx={{ fontWeight: 700, fontSize: '0.75rem' }}>Check Name</TableCell>
                  <TableCell sx={{ fontWeight: 700, fontSize: '0.75rem' }}>Category</TableCell>
                  <TableCell sx={{ fontWeight: 700, fontSize: '0.75rem' }}>HTTP</TableCell>
                  <TableCell sx={{ fontWeight: 700, fontSize: '0.75rem' }}>Latency</TableCell>
                  <TableCell sx={{ fontWeight: 700, fontSize: '0.75rem' }}>Details</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {result.testResults.map((tr, i) => (
                  <TableRow key={i} sx={{ '&:hover': { bgcolor: 'rgba(255,255,255,0.02)' } }}>
                    <TableCell>
                      {tr.passed ? (
                        <Chip
                          icon={<Check size={12} />}
                          label="PASS"
                          size="small"
                          color="success"
                          sx={{ height: 22, fontSize: '0.7rem', fontWeight: 700 }}
                        />
                      ) : (
                        <Chip
                          icon={<X size={12} />}
                          label="FAIL"
                          size="small"
                          color="error"
                          sx={{ height: 22, fontSize: '0.7rem', fontWeight: 700 }}
                        />
                      )}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.82rem' }}>
                      {tr.checkName}
                    </TableCell>
                    <TableCell>
                      <Chip
                        icon={getCategoryIcon(tr.category)}
                        label={tr.category}
                        size="small"
                        variant="outlined"
                        sx={{
                          height: 22,
                          fontSize: '0.68rem',
                          fontWeight: 600,
                          borderColor: getCategoryColor(tr.category),
                          color: getCategoryColor(tr.category),
                        }}
                      />
                    </TableCell>
                    <TableCell>
                      {tr.httpStatus > 0 && (
                        <Chip
                          label={tr.httpStatus}
                          size="small"
                          color={tr.httpStatus < 400 ? 'success' : 'error'}
                          sx={{ height: 20, fontSize: '0.7rem', fontWeight: 700 }}
                        />
                      )}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.8rem' }}>
                      {tr.responseTimeMs > 0 ? `${tr.responseTimeMs} ms` : '—'}
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.75rem', maxWidth: 300 }}>
                      {tr.passed ? (
                        <Typography variant="caption" color="success.main" sx={{ fontWeight: 600 }}>
                          {tr.details}
                        </Typography>
                      ) : (
                        <Typography variant="caption" color="error.main" sx={{ fontWeight: 600 }}>
                          {tr.errorMessage}
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>

          {/* Generated Playwright Script */}
          {result.generatedPlaywrightScript && (
            <Card sx={{ p: 3 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Sparkles size={20} color="#F59E0B" />
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    Generated Playwright E2E Script
                  </Typography>
                  <Chip label="Auto-Generated" color="warning" size="small" variant="outlined" sx={{ fontSize: '0.68rem' }} />
                </Box>
                <Stack direction="row" spacing={1}>
                  <Tooltip title={copied ? 'Copied!' : 'Copy to clipboard'}>
                    <IconButton onClick={handleCopyScript} size="small" sx={{ border: '1px solid', borderColor: 'divider' }}>
                      {copied ? <Check size={16} color="#10B981" /> : <Copy size={16} />}
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Download as .spec.ts file">
                    <IconButton onClick={handleDownloadScript} size="small" sx={{ border: '1px solid', borderColor: 'divider' }}>
                      <Download size={16} />
                    </IconButton>
                  </Tooltip>
                  <Button
                    size="small"
                    variant="text"
                    onClick={() => setShowScript(!showScript)}
                    endIcon={showScript ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    sx={{ fontWeight: 700, fontSize: '0.75rem' }}
                  >
                    {showScript ? 'Collapse' : 'Expand Script'}
                  </Button>
                </Stack>
              </Box>

              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                Ready-to-run Playwright test file tailored to your target URL{showLoginFields ? ' and login flow' : ''}. 
                Download and add to your <code>tests/</code> directory, then run with <code>npx playwright test</code>.
              </Typography>

              <Collapse in={showScript}>
                <Paper
                  sx={{
                    p: 2.5,
                    borderRadius: 2,
                    bgcolor: 'rgba(0, 0, 0, 0.4)',
                    border: '1px solid rgba(255, 255, 255, 0.06)',
                    maxHeight: 500,
                    overflow: 'auto',
                  }}
                >
                  <pre style={{ margin: 0, fontFamily: 'JetBrains Mono, monospace', fontSize: '0.78rem', lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>
                    {result.generatedPlaywrightScript}
                  </pre>
                </Paper>
              </Collapse>
            </Card>
          )}

          {/* Failure Summary */}
          {result.failedChecks > 0 && (
            <Card sx={{ p: 3, border: '1px solid rgba(239, 68, 68, 0.2)' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <X size={20} color="#EF4444" />
                <Typography variant="h6" sx={{ fontWeight: 700, color: 'error.main' }}>
                  Failed Checks Summary
                </Typography>
              </Box>
              <Stack spacing={1}>
                {result.testResults
                  .filter((tr) => !tr.passed)
                  .map((tr, i) => (
                    <Alert key={i} severity="error" sx={{ borderRadius: 2, fontSize: '0.82rem' }}>
                      <strong>{tr.checkName}</strong> — {tr.errorMessage}
                    </Alert>
                  ))}
              </Stack>
            </Card>
          )}
        </>
      )}
    </Stack>
  );
}
