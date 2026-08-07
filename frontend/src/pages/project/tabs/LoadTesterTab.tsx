import { useState } from 'react';
import {
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Grid,
  LinearProgress,
  Paper,
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
} from 'lucide-react';
import { runLoadTest, type LoadTestResponse } from '../../../api/loadTestApi';
import { useToast } from '../../../context/ToastContext';

export function LoadTesterTab({ defaultApiUrl }: { defaultApiUrl?: string }) {
  const { showSuccess, showError } = useToast();
  const [targetUrl, setTargetUrl] = useState(defaultApiUrl || 'https://api.example.com/v1/health');
  const [vus, setVus] = useState<number>(50);
  const [duration, setDuration] = useState<number>(30);
  const [rampUp] = useState<number>(5);

  const [isRunning, setIsRunning] = useState(false);
  const [loadResult, setLoadResult] = useState<LoadTestResponse | null>(null);
  const [copiedK6, setCopiedK6] = useState(false);

  const handleStartLoadTest = async () => {
    if (!targetUrl) return;
    setIsRunning(true);
    try {
      const data = await runLoadTest(targetUrl, vus, duration, rampUp);
      setLoadResult(data);
      showSuccess(`Load test benchmark completed for ${targetUrl}!`);
    } catch {
      showError('Load test simulation failed. Check endpoint accessibility.');
    } finally {
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
            Safe Load & Stress Testing Engine
          </Typography>
          <Chip label="Dynamic Benchmark Engine" color="warning" size="small" variant="outlined" />
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Configure virtual users (VUs), test durations, and target endpoints. Evaluates throughput, tail latencies (p95/p99), rate limit headers, and generates executable k6 scripts.
        </Typography>

        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 5 }}>
            <TextField
              label="Target Endpoint URL"
              fullWidth
              size="small"
              value={targetUrl}
              onChange={(e) => setTargetUrl(e.target.value)}
              disabled={isRunning}
            />
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
          <Grid size={{ xs: 12, sm: 4, md: 2.5 }}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
              DURATION: {duration}s (Ramp: {rampUp}s)
            </Typography>
            <Slider
              value={duration}
              onChange={(_e, v) => setDuration(v as number)}
              min={10}
              max={300}
              step={10}
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
              {isRunning ? 'Benchmarking…' : 'Run Load Test'}
            </Button>
          </Grid>
        </Grid>

        {isRunning && (
          <Box sx={{ mt: 3 }}>
            <LinearProgress color="warning" sx={{ height: 6, borderRadius: 3 }} />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              Ramping up to {vus} VUs over {rampUp}s... Measuring latency percentiles & rate limit header responses...
            </Typography>
          </Box>
        )}
      </Card>

      {/* Execution Results */}
      {loadResult && (
        <>
          <Grid container spacing={2.5}>
            <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
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

            <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
              <Paper sx={{ p: 2.5, textAlign: 'center', borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  AVG LATENCY
                </Typography>
                <Typography variant="h3" sx={{ fontWeight: 800, color: 'success.main', mt: 0.5 }}>
                  {loadResult.avgLatencyMs} ms
                </Typography>
                <Typography variant="caption" color="text.secondary">Median Response</Typography>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
              <Paper sx={{ p: 2.5, textAlign: 'center', borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  95TH PERCENTILE (P95)
                </Typography>
                <Typography variant="h3" sx={{ fontWeight: 800, color: 'warning.main', mt: 0.5 }}>
                  {loadResult.p95Ms} ms
                </Typography>
                <Typography variant="caption" color="text.secondary">p99: {loadResult.p99Ms} ms</Typography>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
              <Paper sx={{ p: 2.5, textAlign: 'center', borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  SUCCESS RATE
                </Typography>
                <Typography variant="h3" sx={{ fontWeight: 800, color: 'success.main', mt: 0.5 }}>
                  {loadResult.successRatePercent.toFixed(1)}%
                </Typography>
                <Typography variant="caption" color="text.secondary">Error: {loadResult.errorRatePercent.toFixed(1)}%</Typography>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
              <Paper sx={{ p: 2.5, textAlign: 'center', borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  RATE LIMITING
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 800, color: 'info.main', mt: 1.5 }}>
                  {loadResult.rateLimitStatus}
                </Typography>
              </Paper>
            </Grid>
          </Grid>

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
                    Rate Limit Policy Audit Results
                  </Typography>
                </Box>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Policy Name</TableCell>
                      <TableCell>Evaluated Value</TableCell>
                      <TableCell>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {loadResult.rateLimitPolicies.map((r) => (
                      <TableRow key={r.policy}>
                        <TableCell sx={{ fontWeight: 600 }}>{r.policy}</TableCell>
                        <TableCell><code style={{ fontFamily: 'JetBrains Mono', fontSize: 11 }}>{r.value}</code></TableCell>
                        <TableCell>
                          <Chip label={r.status} color="success" size="small" sx={{ height: 20, fontSize: 11 }} />
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
