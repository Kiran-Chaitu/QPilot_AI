import { useState } from 'react';
import {
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  FormControl,
  Grid,
  InputLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Select,
  Slider,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import {
  Gauge,
  Play,
  FileCode,
  Download,
  Copy,
  Check,
  ShieldCheck,
  BarChart3,
  Timer,
} from 'lucide-react';
import { runLoadTest, type LoadTestResponse } from '../../../api/loadTestApi';
import { useToast } from '../../../context/ToastContext';

export function LoadTesterTab({ defaultApiUrl }: { defaultApiUrl?: string }) {
  const { showSuccess, showError } = useToast();
  const [targetUrl, setTargetUrl] = useState(defaultApiUrl || '');
  const [vus, setVus] = useState<number>(50);
  const [duration, setDuration] = useState<number>(30);
  const [rampUp] = useState<number>(5);
  const [httpMethod, setHttpMethod] = useState('GET');

  const [isRunning, setIsRunning] = useState(false);
  const [loadResult, setLoadResult] = useState<LoadTestResponse | null>(null);
  const [copiedK6, setCopiedK6] = useState(false);
  const [progress, setProgress] = useState(0);

  const handleStartLoadTest = async () => {
    if (!targetUrl) return;
    setIsRunning(true);
    setProgress(0);
    setLoadResult(null);

    // Simulate progress bar since the backend takes time for real load testing
    const progressInterval = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 90) return prev;
        // Progress accelerates based on expected duration
        const increment = Math.max(0.5, 80 / (duration + rampUp));
        return Math.min(prev + increment, 90);
      });
    }, 1000);

    try {
      const data = await runLoadTest(targetUrl, vus, duration, rampUp, httpMethod);
      setLoadResult(data);
      setProgress(100);
      showSuccess(
        `Load test completed! ${data.totalRequests} requests sent, ` +
        `${data.successfulRequests} successful, avg latency ${data.avgLatencyMs}ms`
      );
    } catch {
      showError('Load test failed. Check endpoint accessibility.');
    } finally {
      clearInterval(progressInterval);
      setIsRunning(false);
    }
  };

  const handleCopyK6 = () => {
    if (!loadResult?.k6Script) return;
    navigator.clipboard.writeText(loadResult.k6Script);
    setCopiedK6(true);
    showSuccess('k6 script copied to clipboard!');
    setTimeout(() => setCopiedK6(false), 2000);
  };

  const handleDownloadK6 = () => {
    if (!loadResult?.k6Script) return;
    const blob = new Blob([loadResult.k6Script], { type: 'text/javascript;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'loadtest_script.js';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    showSuccess('Downloaded k6 script!');
  };

  const getStatusCodeColor = (code: number): 'success' | 'warning' | 'error' | 'default' => {
    if (code >= 200 && code < 300) return 'success';
    if (code >= 300 && code < 400) return 'warning';
    if (code >= 400) return 'error';
    return 'default';
  };

  return (
    <Stack spacing={3}>
      {/* Configuration Header */}
      <Card
        sx={{
          p: 3,
          border: '1px solid rgba(245, 158, 11, 0.3)',
          background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.1) 0%, rgba(239, 68, 68, 0.05) 100%)',
          backdropFilter: 'blur(16px)',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
          <Gauge size={24} color="#F59E0B" />
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            Real Load & Stress Testing Engine
          </Typography>
          <Chip label="Live HTTP Benchmark" color="warning" size="small" variant="outlined" />
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Sends real concurrent HTTP requests to your endpoint using virtual threads. Measures actual throughput,
          real latency percentiles (p50/p90/p95/p99), detects genuine rate limit headers (429, X-RateLimit-*),
          and generates executable k6 scripts based on observed performance.
        </Typography>

        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              label="Target Endpoint URL"
              fullWidth
              size="small"
              value={targetUrl}
              onChange={(e) => setTargetUrl(e.target.value)}
              disabled={isRunning}
              placeholder="https://api.example.com/v1/health"
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 3, md: 1.5 }}>
            <FormControl fullWidth size="small">
              <InputLabel>Method</InputLabel>
              <Select
                value={httpMethod}
                label="Method"
                onChange={(e) => setHttpMethod(e.target.value)}
                disabled={isRunning}
              >
                <MenuItem value="GET">GET</MenuItem>
                <MenuItem value="HEAD">HEAD</MenuItem>
                <MenuItem value="POST">POST</MenuItem>
                <MenuItem value="PUT">PUT</MenuItem>
                <MenuItem value="DELETE">DELETE</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 12, sm: 4, md: 2.5 }}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
              VIRTUAL USERS (VUs): {vus}
            </Typography>
            <Slider
              value={vus}
              onChange={(_e, v) => setVus(v as number)}
              min={5}
              max={500}
              step={5}
              disabled={isRunning}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 4, md: 2 }}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
              DURATION: {duration}s (Ramp: {rampUp}s)
            </Typography>
            <Slider
              value={duration}
              onChange={(_e, v) => setDuration(v as number)}
              min={10}
              max={120}
              step={5}
              disabled={isRunning}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 4, md: 2 }} sx={{ display: 'flex', alignItems: 'center' }}>
            <Button
              fullWidth
              variant="contained"
              color="warning"
              size="large"
              startIcon={isRunning ? <CircularProgress size={16} color="inherit" /> : <Play size={18} />}
              onClick={handleStartLoadTest}
              disabled={isRunning || !targetUrl}
              sx={{ fontWeight: 700, borderRadius: 2 }}
            >
              {isRunning ? 'Testing…' : 'Run Load Test'}
            </Button>
          </Grid>
        </Grid>

        {isRunning && (
          <Box sx={{ mt: 3 }}>
            <LinearProgress
              variant="determinate"
              value={progress}
              color="warning"
              sx={{ height: 6, borderRadius: 3 }}
            />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              Sending real concurrent {httpMethod} requests to {targetUrl} — Ramping up to {vus} VUs
              over {rampUp}s, sustaining for {duration}s... ({Math.round(progress)}%)
            </Typography>
          </Box>
        )}
      </Card>

      {/* Execution Results */}
      {loadResult && (
        <>
          {/* Core Metrics */}
          <Grid container spacing={2.5}>
            <Grid size={{ xs: 12, sm: 6, md: 2 }}>
              <Paper sx={{ p: 2.5, textAlign: 'center', borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  THROUGHPUT
                </Typography>
                <Typography variant="h3" sx={{ fontWeight: 800, color: 'primary.main', mt: 0.5 }}>
                  {loadResult.rpsThroughput}
                </Typography>
                <Typography variant="caption" color="text.secondary">Req / second</Typography>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 2 }}>
              <Paper sx={{ p: 2.5, textAlign: 'center', borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  AVG LATENCY
                </Typography>
                <Typography variant="h3" sx={{ fontWeight: 800, color: 'success.main', mt: 0.5 }}>
                  {loadResult.avgLatencyMs} ms
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Min: {loadResult.minLatencyMs}ms / Max: {loadResult.maxLatencyMs}ms
                </Typography>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 2 }}>
              <Paper sx={{ p: 2.5, textAlign: 'center', borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  P95 / P99 LATENCY
                </Typography>
                <Typography variant="h3" sx={{ fontWeight: 800, color: 'warning.main', mt: 0.5 }}>
                  {loadResult.p95Ms} ms
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  p50: {loadResult.p50Ms}ms / p90: {loadResult.p90Ms}ms / p99: {loadResult.p99Ms}ms
                </Typography>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 2 }}>
              <Paper sx={{ p: 2.5, textAlign: 'center', borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  TOTAL REQUESTS
                </Typography>
                <Typography variant="h3" sx={{ fontWeight: 800, color: 'info.main', mt: 0.5 }}>
                  {loadResult.totalRequests}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  <span style={{ color: '#10B981' }}>{loadResult.successfulRequests} OK</span>
                  {' / '}
                  <span style={{ color: '#EF4444' }}>{loadResult.failedRequests} Failed</span>
                </Typography>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 2 }}>
              <Paper sx={{ p: 2.5, textAlign: 'center', borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  SUCCESS RATE
                </Typography>
                <Typography variant="h3" sx={{ fontWeight: 800, color: loadResult.successRatePercent >= 95 ? 'success.main' : loadResult.successRatePercent >= 80 ? 'warning.main' : 'error.main', mt: 0.5 }}>
                  {loadResult.successRatePercent.toFixed(1)}%
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Error: {loadResult.errorRatePercent.toFixed(1)}%
                </Typography>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 2 }}>
              <Paper sx={{ p: 2.5, textAlign: 'center', borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  RATE LIMITING
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 800, color: loadResult.rateLimitStatus.includes('429') ? 'error.main' : 'info.main', mt: 1.5 }}>
                  {loadResult.rateLimitStatus}
                </Typography>
              </Paper>
            </Grid>
          </Grid>

          {/* Status Code Distribution */}
          {loadResult.statusCodeDistribution && Object.keys(loadResult.statusCodeDistribution).length > 0 && (
            <Card sx={{ p: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <BarChart3 size={20} color="#6366F1" />
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Response Status Code Distribution
                </Typography>
              </Box>
              <Stack direction="row" spacing={1.5} sx={{ flexWrap: 'wrap', gap: 1 }}>
                {Object.entries(loadResult.statusCodeDistribution).map(([code, count]) => (
                  <Chip
                    key={code}
                    label={`HTTP ${code}: ${count} requests`}
                    color={getStatusCodeColor(Number(code))}
                    variant="outlined"
                    sx={{ fontWeight: 700, fontSize: '0.82rem' }}
                  />
                ))}
              </Stack>
              <Box sx={{ mt: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                <Timer size={14} color="#A1A1AA" />
                <Typography variant="caption" color="text.secondary">
                  Test duration: {loadResult.durationSeconds}s with {loadResult.vus} virtual users
                  (ramp-up: {loadResult.rampUpSeconds}s)
                </Typography>
              </Box>
            </Card>
          )}

          {/* Generated Scripts Section */}
          <Grid container spacing={3}>
            {/* Auto-generated k6 script */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Card sx={{ p: 3 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <FileCode size={20} color="#6366F1" />
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      Generated k6 Executable Script
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={1}>
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={copiedK6 ? <Check size={14} /> : <Copy size={14} />}
                      onClick={handleCopyK6}
                    >
                      {copiedK6 ? 'Copied' : 'Copy'}
                    </Button>
                    <Button
                      size="small"
                      variant="contained"
                      startIcon={<Download size={14} />}
                      onClick={handleDownloadK6}
                    >
                      Script
                    </Button>
                  </Stack>
                </Box>
                <Box
                  component="pre"
                  sx={{
                    bgcolor: '#0d1117',
                    color: '#c9d1d9',
                    p: 2,
                    borderRadius: 2,
                    fontSize: 12,
                    fontFamily: 'JetBrains Mono, monospace',
                    maxHeight: 280,
                    overflowY: 'auto',
                  }}
                >
                  {loadResult.k6Script}
                </Box>
              </Card>
            </Grid>

            {/* Rate Limit Analysis Report */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Card sx={{ p: 3 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                  <ShieldCheck size={20} color="#10B981" />
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    Rate Limit Detection Results
                  </Typography>
                  <Chip
                    label="Real Header Analysis"
                    size="small"
                    color="success"
                    variant="outlined"
                    sx={{ fontSize: '0.68rem' }}
                  />
                </Box>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Policy / Header</TableCell>
                      <TableCell>Observed Value</TableCell>
                      <TableCell>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {loadResult.rateLimitPolicies.map((r) => (
                      <TableRow key={r.policy}>
                        <TableCell sx={{ fontWeight: 600 }}>{r.policy}</TableCell>
                        <TableCell><code style={{ fontFamily: 'JetBrains Mono', fontSize: 11 }}>{r.value}</code></TableCell>
                        <TableCell>
                          <Chip
                            label={r.status}
                            color={
                              r.status.includes('Active') || r.status.includes('Detected')
                                ? 'success'
                                : r.status.includes('Not')
                                ? 'default'
                                : 'warning'
                            }
                            size="small"
                            sx={{ height: 20, fontSize: 11 }}
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Card>
            </Grid>
          </Grid>
        </>
      )}
    </Stack>
  );
}
