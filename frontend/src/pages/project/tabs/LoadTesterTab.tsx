import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Checkbox,
  Chip,
  CircularProgress,
  Collapse,
  Divider,
  FormControl,
  FormControlLabel,
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
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Activity,
  ChevronDown,
  ChevronUp,
  Gauge,
  History,
  Play,
  ShieldAlert,
  Square,
  Timer,
} from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { getLoadTestRun, listLoadTestRuns, startLoadTest, stopLoadTest } from '../../../api/loadTestApi';
import { extractErrorMessage } from '../../../api/httpClient';
import { useToast } from '../../../context/ToastContext';
import { EmptyState, ErrorState } from '../../../components/common/StateViews';
import { brand, status as statusColors } from '../../../theme/palette';
import type { LoadTestRun, LoadTestStatus } from '../../../types/loadtest';

/** How often a running test is polled for live metrics. */
const POLL_INTERVAL_MS = 1500;

const STATUS_META: Record<LoadTestStatus, { label: string; color: string; description: string }> = {
  CONFIGURED: { label: 'Configured', color: '#8B93A7', description: 'Accepted and queued; traffic has not started yet.' },
  RUNNING: { label: 'Running', color: brand.primary, description: 'Generating traffic now. Metrics below are live and still moving.' },
  COMPLETED: { label: 'Completed', color: statusColors.success, description: 'Ran to the end of the configured plan. Metrics are final.' },
  CANCELLED: { label: 'Cancelled', color: statusColors.warning, description: 'Stopped early. Metrics cover only the traffic actually sent.' },
  FAILED: { label: 'Failed', color: statusColors.error, description: 'The run could not complete.' },
};

function StatusChip({ runStatus }: { runStatus: LoadTestStatus }) {
  const meta = STATUS_META[runStatus];
  return (
    <Tooltip title={meta.description}>
      <Chip
        size="small"
        label={meta.label}
        icon={runStatus === 'RUNNING' ? <span className="qp-live-dot" style={{ marginLeft: 8 }} /> : undefined}
        sx={{ fontWeight: 750, color: meta.color, bgcolor: `${meta.color}1F`, border: `1px solid ${meta.color}44`, cursor: 'help' }}
      />
    </Tooltip>
  );
}

function Metric({ label, value, sub, color }: { label: string; value: string; sub?: string; color?: string }) {
  return (
    <Paper sx={{ p: 2, borderRadius: 3, border: '1px solid', borderColor: 'divider', height: '100%' }}>
      <Typography variant="overline" color="text.secondary" sx={{ display: 'block', lineHeight: 1.4 }}>
        {label}
      </Typography>
      <Typography variant="h5" sx={{ fontWeight: 800, color: color ?? 'text.primary', my: 0.25, lineHeight: 1.2 }}>
        {value}
      </Typography>
      {sub && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.4 }}>
          {sub}
        </Typography>
      )}
    </Paper>
  );
}

export function LoadTesterTab({ defaultApiUrl, projectId }: { defaultApiUrl?: string; projectId?: number }) {
  const { showSuccess, showError, showInfo } = useToast();

  const [targetUrl, setTargetUrl] = useState(defaultApiUrl ?? '');
  const [httpMethod, setHttpMethod] = useState('GET');
  const [virtualUsers, setVirtualUsers] = useState(20);
  const [durationSeconds, setDurationSeconds] = useState(20);
  const [rampUpSeconds, setRampUpSeconds] = useState(3);
  const [rampDownSeconds, setRampDownSeconds] = useState(2);
  const [targetRps, setTargetRps] = useState<string>('');
  const [timeoutSeconds, setTimeoutSeconds] = useState<string>('10');
  const [headersText, setHeadersText] = useState('');
  const [requestBody, setRequestBody] = useState('');
  const [authorized, setAuthorized] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const [run, setRun] = useState<LoadTestRun | null>(null);
  const [history, setHistory] = useState<LoadTestRun[]>([]);
  const [isStarting, setIsStarting] = useState(false);
  const [isStopping, setIsStopping] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Held in a ref so the polling effect can clear a timer it did not itself create.
  const pollTimer = useRef<number | null>(null);

  const loadHistory = useCallback(async () => {
    try {
      setHistory(await listLoadTestRuns());
    } catch {
      // History is supplementary; a failure here must not obscure the current run.
    }
  }, []);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  useEffect(() => {
    if (defaultApiUrl && !targetUrl) {
      setTargetUrl(defaultApiUrl);
    }
    // Only seeds an empty field, so it never overwrites something the user typed.
  }, [defaultApiUrl, targetUrl]);

  /**
   * Polls the run while it is in flight.
   *
   * <p>This is what makes the progress bar real: every tick reads metrics the server measured from
   * completed requests. The previous implementation animated a client-side timer that was unrelated to
   * what the backend was doing.
   */
  useEffect(() => {
    if (!run || (run.status !== 'RUNNING' && run.status !== 'CONFIGURED')) {
      return;
    }
    let cancelled = false;

    const tick = async () => {
      try {
        const updated = await getLoadTestRun(run.id);
        if (cancelled) return;
        setRun(updated);
        if (updated.status === 'RUNNING' || updated.status === 'CONFIGURED') {
          pollTimer.current = window.setTimeout(tick, POLL_INTERVAL_MS);
        } else {
          loadHistory();
          if (updated.status === 'COMPLETED') {
            showSuccess(
              `Load test finished: ${updated.totalRequests} requests, ${updated.successRatePercent.toFixed(1)}% success, p95 ${updated.p95LatencyMs}ms.`,
            );
          } else if (updated.status === 'CANCELLED') {
            showInfo(`Load test stopped. ${updated.totalRequests} requests were sent before the stop.`);
          } else if (updated.status === 'FAILED') {
            showError(updated.errorMessage ?? 'The load test failed.');
          }
        }
      } catch (err) {
        if (!cancelled) {
          setError(extractErrorMessage(err, 'Lost contact with the load test while polling for progress.'));
        }
      }
    };

    pollTimer.current = window.setTimeout(tick, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      if (pollTimer.current) {
        window.clearTimeout(pollTimer.current);
        pollTimer.current = null;
      }
    };
  }, [run, loadHistory, showSuccess, showError, showInfo]);

  /** Parses the "Name: value" header textarea, ignoring blank and malformed lines. */
  const parseHeaders = (): Record<string, string> => {
    const headers: Record<string, string> = {};
    headersText.split('\n').forEach((line) => {
      const separator = line.indexOf(':');
      if (separator > 0) {
        const name = line.slice(0, separator).trim();
        const value = line.slice(separator + 1).trim();
        if (name && value) headers[name] = value;
      }
    });
    return headers;
  };

  const handleStart = async () => {
    setError(null);
    setIsStarting(true);
    try {
      const started = await startLoadTest({
        targetUrl: targetUrl.trim(),
        httpMethod,
        virtualUsers,
        durationSeconds,
        rampUpSeconds,
        rampDownSeconds,
        targetRequestsPerSecond: targetRps ? Number(targetRps) : null,
        requestTimeoutSeconds: timeoutSeconds ? Number(timeoutSeconds) : null,
        headers: parseHeaders(),
        requestBody: requestBody.trim() ? requestBody : null,
        projectId: projectId ?? null,
        authorizedTarget: authorized,
      });
      setRun(started);
      if (started.clampNotes) {
        showInfo(started.clampNotes);
      }
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not start the load test.'));
    } finally {
      setIsStarting(false);
    }
  };

  const handleStop = async () => {
    if (!run) return;
    setIsStopping(true);
    try {
      setRun(await stopLoadTest(run.id));
      showInfo('Stop requested. The run will finalize with the metrics measured so far.');
    } catch (err) {
      showError(extractErrorMessage(err, 'Could not stop the run.'));
    } finally {
      setIsStopping(false);
    }
  };

  const isActive = run?.status === 'RUNNING' || run?.status === 'CONFIGURED';
  const canStart = targetUrl.trim().length > 0 && authorized && !isActive && !isStarting;

  const statusEntries = run ? Object.entries(run.statusCodeDistribution ?? {}) : [];

  return (
    <Stack spacing={3}>
      {/* ── Configuration ─────────────────────────────────────────────── */}
      <Card className="qp-gradient-border" sx={{ p: { xs: 2.5, md: 3 } }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 1 }}>
          <Gauge size={22} color={brand.primary} />
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            Load &amp; performance test
          </Typography>
          <Chip size="small" variant="outlined" color="secondary" label="Real HTTP traffic" sx={{ fontWeight: 700 }} />
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5, maxWidth: 780 }}>
          Sends real concurrent requests and reports only what it measured: throughput from elapsed time, latency
          percentiles from the full sample of completed requests, and the observed HTTP status distribution. Nothing is
          modelled or simulated.
        </Typography>

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 7 }}>
            <TextField
              label="Target URL"
              fullWidth
              value={targetUrl}
              onChange={(event) => setTargetUrl(event.target.value)}
              disabled={isActive}
              placeholder="https://staging.example.com/api/health"
              helperText="Must be a service you own or are authorized to test."
            />
          </Grid>
          <Grid size={{ xs: 6, md: 2 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="lt-method">Method</InputLabel>
              <Select
                labelId="lt-method"
                value={httpMethod}
                label="Method"
                onChange={(event) => setHttpMethod(event.target.value)}
                disabled={isActive}
              >
                {['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'].map((method) => (
                  <MenuItem key={method} value={method}>
                    {method}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 6, md: 3 }}>
            <TextField
              label="Target requests/sec"
              fullWidth
              value={targetRps}
              onChange={(event) => setTargetRps(event.target.value.replace(/\D/g, ''))}
              disabled={isActive}
              placeholder="unlimited"
              helperText="Optional rate ceiling"
            />
          </Grid>

          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Typography variant="caption" sx={{ fontWeight: 750 }}>
              Virtual users: {virtualUsers}
            </Typography>
            <Slider value={virtualUsers} onChange={(_, value) => setVirtualUsers(value as number)} min={1} max={200} disabled={isActive} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Typography variant="caption" sx={{ fontWeight: 750 }}>
              Duration: {durationSeconds}s
            </Typography>
            <Slider value={durationSeconds} onChange={(_, value) => setDurationSeconds(value as number)} min={5} max={120} step={5} disabled={isActive} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Typography variant="caption" sx={{ fontWeight: 750 }}>
              Ramp-up: {rampUpSeconds}s
            </Typography>
            <Slider value={rampUpSeconds} onChange={(_, value) => setRampUpSeconds(value as number)} min={0} max={60} disabled={isActive} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Typography variant="caption" sx={{ fontWeight: 750 }}>
              Ramp-down: {rampDownSeconds}s
            </Typography>
            <Slider value={rampDownSeconds} onChange={(_, value) => setRampDownSeconds(value as number)} min={0} max={60} disabled={isActive} />
          </Grid>
        </Grid>

        <Button
          size="small"
          onClick={() => setShowAdvanced((previous) => !previous)}
          endIcon={showAdvanced ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          sx={{ mt: 1.5, fontWeight: 700 }}
        >
          {showAdvanced ? 'Hide' : 'Show'} headers, body &amp; timeout
        </Button>
        <Collapse in={showAdvanced}>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                label="Request headers"
                fullWidth
                multiline
                minRows={3}
                value={headersText}
                onChange={(event) => setHeadersText(event.target.value)}
                disabled={isActive}
                placeholder={'Authorization: Bearer …\nX-Tenant: acme'}
                helperText="One per line, Name: value"
              />
            </Grid>
            <Grid size={{ xs: 12, md: 5 }}>
              <TextField
                label="Request body"
                fullWidth
                multiline
                minRows={3}
                value={requestBody}
                onChange={(event) => setRequestBody(event.target.value)}
                disabled={isActive}
                placeholder='{"query":"example"}'
                helperText="Sent with POST/PUT/PATCH"
              />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                label="Per-request timeout (s)"
                fullWidth
                value={timeoutSeconds}
                onChange={(event) => setTimeoutSeconds(event.target.value.replace(/\D/g, ''))}
                disabled={isActive}
              />
            </Grid>
          </Grid>
        </Collapse>

        {/*
          Authorization gate. The server enforces this too, but requiring an explicit action here means the
          user makes a conscious decision before generating sustained traffic at a real host.
        */}
        <Alert severity="warning" variant="outlined" icon={<ShieldAlert size={18} />} sx={{ mt: 2.5, borderRadius: 3 }}>
          <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
            Only test infrastructure you are authorized to test
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            Sustained traffic against a service you do not control is indistinguishable from a denial-of-service
            attack and may breach your provider&apos;s terms. QPilot caps users, duration, rate and total requests, but
            the choice of target is yours.
          </Typography>
          <FormControlLabel
            sx={{ mt: 1 }}
            control={<Checkbox checked={authorized} onChange={(event) => setAuthorized(event.target.checked)} disabled={isActive} size="small" />}
            label={
              <Typography variant="body2" sx={{ fontWeight: 650 }}>
                I own, or am explicitly authorized to load test, this target
              </Typography>
            }
          />
        </Alert>

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 2.5 }}>
          <Button
            variant="contained"
            size="large"
            startIcon={isStarting ? <CircularProgress size={16} color="inherit" /> : <Play size={18} />}
            onClick={handleStart}
            disabled={!canStart}
            sx={{ fontWeight: 750, minWidth: 190 }}
          >
            {isStarting ? 'Starting…' : 'Start load test'}
          </Button>
          {isActive && (
            <Button
              variant="outlined"
              color="error"
              size="large"
              startIcon={isStopping ? <CircularProgress size={16} color="inherit" /> : <Square size={16} />}
              onClick={handleStop}
              disabled={isStopping}
              sx={{ fontWeight: 750 }}
            >
              {isStopping ? 'Stopping…' : 'Stop test'}
            </Button>
          )}
          {!authorized && !isActive && (
            <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>
              Confirm authorization above to enable the run.
            </Typography>
          )}
        </Stack>

        {error && (
          <Box sx={{ mt: 2 }}>
            <ErrorState title="Load test could not start" message={error} onRetry={handleStart} retryLabel="Try again" />
          </Box>
        )}
      </Card>

      {/* ── Live / final results ──────────────────────────────────────── */}
      {run && (
        <>
          <Card sx={{ p: { xs: 2, md: 2.5 } }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between', mb: 1.5 }}>
              <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', minWidth: 0 }}>
                <StatusChip runStatus={run.status} />
                <Typography variant="body2" className="qp-truncate" sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem' }}>
                  {run.httpMethod} {run.targetUrl}
                </Typography>
              </Stack>
              <Typography variant="caption" color="text.secondary">
                {run.virtualUsers} users · {run.durationSeconds}s sustain · ramp {run.rampUpSeconds}s/{run.rampDownSeconds}s
                {run.targetRequestsPerSecond ? ` · capped at ${run.targetRequestsPerSecond} req/s` : ''}
              </Typography>
            </Stack>

            {isActive && (
              <Box sx={{ mb: 1 }}>
                <LinearProgress variant="determinate" value={run.progressPercent} />
                <Typography variant="caption" color="text.secondary" sx={{ mt: 0.75, display: 'block' }}>
                  {run.progressPercent}% through the configured plan — {run.totalRequests} requests completed so far.
                  These are live measurements, not an estimate.
                </Typography>
              </Box>
            )}

            {run.clampNotes && (
              <Alert severity="info" variant="outlined" sx={{ mt: 1, borderRadius: 2.5 }}>
                <Typography variant="caption">{run.clampNotes}</Typography>
              </Alert>
            )}
            {run.errorMessage && (
              <Alert severity={run.status === 'FAILED' ? 'error' : 'warning'} variant="outlined" sx={{ mt: 1, borderRadius: 2.5 }}>
                <Typography variant="caption">{run.errorMessage}</Typography>
              </Alert>
            )}
          </Card>

          <Grid container spacing={2} className="qp-stagger">
            <Grid size={{ xs: 6, sm: 4, md: 2 }}>
              <Metric label="Throughput" value={`${run.requestsPerSecond}`} sub="requests / second (measured)" color={brand.primary} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2 }}>
              <Metric label="Requests" value={`${run.totalRequests}`} sub={`${run.successfulRequests} ok · ${run.failedRequests} failed`} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2 }}>
              <Metric
                label="Success rate"
                value={`${run.successRatePercent.toFixed(1)}%`}
                sub={`error rate ${run.errorRatePercent.toFixed(1)}%`}
                color={run.successRatePercent >= 99 ? statusColors.success : run.successRatePercent >= 90 ? statusColors.warning : statusColors.error}
              />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2 }}>
              <Metric label="Avg latency" value={`${run.avgLatencyMs} ms`} sub={`min ${run.minLatencyMs} · max ${run.maxLatencyMs}`} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2 }}>
              <Metric label="p95 latency" value={`${run.p95LatencyMs} ms`} sub={`p50 ${run.p50LatencyMs} · p99 ${run.p99LatencyMs}`} color={statusColors.warning} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2 }}>
              <Metric
                label="Elapsed"
                value={`${(run.actualDurationMs / 1000).toFixed(1)}s`}
                sub={`${(run.totalBytesReceived / 1024).toFixed(0)} KB received`}
              />
            </Grid>
          </Grid>

          {/* Time series — plotted from the per-second buckets the engine recorded. */}
          {run.timeSeries.length > 1 && (
            <Card sx={{ p: { xs: 2, md: 2.5 } }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
                <Activity size={18} color={brand.secondary} />
                <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                  Measured over time
                </Typography>
                <Chip size="small" variant="outlined" label={`${run.timeSeries.length} one-second buckets`} />
              </Stack>

              <Box sx={{ width: '100%', height: 240 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={run.timeSeries} margin={{ top: 6, right: 12, left: -16, bottom: 0 }}>
                    <defs>
                      <linearGradient id="ltRequests" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor={brand.primary} stopOpacity={0.45} />
                        <stop offset="95%" stopColor={brand.primary} stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--qp-border)" vertical={false} />
                    <XAxis dataKey="secondOffset" stroke="var(--qp-text-muted)" fontSize={11} tickLine={false} unit="s" />
                    <YAxis stroke="var(--qp-text-muted)" fontSize={11} tickLine={false} />
                    <RechartsTooltip
                      contentStyle={{ background: 'var(--qp-elevated)', border: '1px solid var(--qp-border)', borderRadius: 10, fontSize: 12 }}
                      labelFormatter={(value) => `${value}s into the run`}
                    />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    <Area type="monotone" dataKey="requests" name="Requests completed" stroke={brand.primary} strokeWidth={2} fill="url(#ltRequests)" />
                    <Area type="monotone" dataKey="errors" name="Errors" stroke={statusColors.error} strokeWidth={2} fillOpacity={0.15} fill={statusColors.error} />
                  </AreaChart>
                </ResponsiveContainer>
              </Box>

              <Divider sx={{ my: 2 }} />

              <Box sx={{ width: '100%', height: 200 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={run.timeSeries} margin={{ top: 6, right: 12, left: -16, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--qp-border)" vertical={false} />
                    <XAxis dataKey="secondOffset" stroke="var(--qp-text-muted)" fontSize={11} tickLine={false} unit="s" />
                    <YAxis stroke="var(--qp-text-muted)" fontSize={11} tickLine={false} unit="ms" />
                    <RechartsTooltip
                      contentStyle={{ background: 'var(--qp-elevated)', border: '1px solid var(--qp-border)', borderRadius: 10, fontSize: 12 }}
                      labelFormatter={(value) => `${value}s into the run`}
                    />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    <Line type="monotone" dataKey="avgLatencyMs" name="Avg latency" stroke={brand.secondary} strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="p95LatencyMs" name="p95 latency" stroke={statusColors.warning} strokeWidth={2} dot={false} strokeDasharray="4 3" />
                  </AreaChart>
                </ResponsiveContainer>
              </Box>
            </Card>
          )}

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Card sx={{ p: 2.5, height: '100%' }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 750, mb: 1.5 }}>
                  Observed status codes
                </Typography>
                {statusEntries.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    No responses recorded yet.
                  </Typography>
                ) : (
                  <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
                    {statusEntries.map(([code, count]) => {
                      const numeric = Number(code);
                      const color =
                        numeric === 0
                          ? statusColors.error
                          : numeric < 300
                            ? statusColors.success
                            : numeric < 400
                              ? statusColors.info
                              : numeric === 429
                                ? statusColors.warning
                                : statusColors.error;
                      return (
                        <Tooltip
                          key={code}
                          title={numeric === 0 ? 'No HTTP response received — DNS failure, connection refused or timeout.' : `HTTP ${code}`}
                        >
                          <Chip
                            label={`${numeric === 0 ? 'No response' : `HTTP ${code}`} — ${count}`}
                            sx={{ fontWeight: 700, color, bgcolor: `${color}18`, border: `1px solid ${color}3D`, cursor: 'help' }}
                          />
                        </Tooltip>
                      );
                    })}
                  </Stack>
                )}
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 2 }}>
                  <Timer size={14} />
                  <Typography variant="caption" color="text.secondary">
                    Throughput is computed from the measured {(run.actualDurationMs / 1000).toFixed(1)}s elapsed, not the
                    configured duration.
                  </Typography>
                </Stack>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <Card sx={{ p: 2.5, height: '100%' }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 750, mb: 1.5 }}>
                  Rate limiting observed during this run
                </Typography>
                {run.rateLimitEvidence ? (
                  <Stack spacing={1.5}>
                    <Alert
                      severity={run.rateLimitEvidence.detected ? 'warning' : 'info'}
                      variant="outlined"
                      sx={{ borderRadius: 2.5 }}
                    >
                      <Typography variant="body2">{run.rateLimitEvidence.verdict}</Typography>
                    </Alert>
                    <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
                      <Chip size="small" variant="outlined" label={`HTTP 429 responses: ${run.rateLimitEvidence.http429Count}`} />
                      <Chip
                        size="small"
                        variant="outlined"
                        label={`RateLimit-Limit: ${run.rateLimitEvidence.rateLimitLimitHeader ?? 'not advertised'}`}
                      />
                      <Chip
                        size="small"
                        variant="outlined"
                        label={`Retry-After: ${run.rateLimitEvidence.retryAfterValues.length > 0 ? run.rateLimitEvidence.retryAfterValues.join(', ') : 'not sent'}`}
                      />
                    </Stack>
                  </Stack>
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    No rate-limit data recorded for this run yet.
                  </Typography>
                )}
              </Card>
            </Grid>
          </Grid>
        </>
      )}

      {!run && !error && (
        <Card>
          <EmptyState
            icon={<Gauge size={24} />}
            title="No load test running"
            description="Configure a target above, confirm you are authorized to test it, and start a run. Progress and metrics appear here live as requests complete."
          />
        </Card>
      )}

      {/* ── History (real past runs) ──────────────────────────────────── */}
      {history.length > 0 && (
        <Card sx={{ p: { xs: 2, md: 2.5 } }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
            <History size={18} />
            <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
              Previous runs
            </Typography>
            <Chip size="small" variant="outlined" label={`${history.length} stored`} />
          </Stack>
          <Box className="qp-scroll-x">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Status</TableCell>
                  <TableCell>Target</TableCell>
                  <TableCell align="right">Users</TableCell>
                  <TableCell align="right">Requests</TableCell>
                  <TableCell align="right">Req/s</TableCell>
                  <TableCell align="right">p95</TableCell>
                  <TableCell align="right">Errors</TableCell>
                  <TableCell>When</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {history.map((item) => (
                  <TableRow
                    key={item.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => setRun(item)}
                  >
                    <TableCell>
                      <StatusChip runStatus={item.status} />
                    </TableCell>
                    <TableCell sx={{ maxWidth: 260 }}>
                      <Typography variant="caption" className="qp-truncate" sx={{ fontFamily: 'var(--font-mono)', display: 'block' }}>
                        {item.httpMethod} {item.targetUrl}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">{item.virtualUsers}</TableCell>
                    <TableCell align="right">{item.totalRequests}</TableCell>
                    <TableCell align="right">{item.requestsPerSecond}</TableCell>
                    <TableCell align="right">{item.p95LatencyMs} ms</TableCell>
                    <TableCell align="right">{item.errorRatePercent.toFixed(1)}%</TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        {new Date(item.createdAt).toLocaleString()}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        </Card>
      )}
    </Stack>
  );
}
