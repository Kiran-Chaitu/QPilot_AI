import { useState } from 'react';
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Card,
  Checkbox,
  Chip,
  CircularProgress,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  LinearProgress,
  MenuItem,
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
import { Info, Play, ShieldAlert, ShieldCheck, Timer, Zap } from 'lucide-react';
import { probeRateLimit, type RateLimitPhaseResult, type RateLimitTestResult } from '../../../api/rateLimitApi';
import { extractErrorMessage } from '../../../api/httpClient';
import { useToast } from '../../../context/ToastContext';
import { EmptyState, ErrorState, NotAvailable } from '../../../components/common/StateViews';
import { status as statusColors } from '../../../theme/palette';

/**
 * Rate-limit probe UI.
 *
 * <p>The interface is written to keep one distinction front and centre: detecting rate limiting proves it
 * exists, but *not* detecting it proves nothing. Every place a negative result is shown, it is phrased as
 * "no evidence at the load applied" and accompanied by the load that was actually applied — because the
 * tempting shorter claim ("this endpoint has no rate limiting") is one the probe cannot support.
 */

function PhaseCard({ phase, title, description }: { phase?: RateLimitPhaseResult; title: string; description: string }) {
  if (!phase) {
    return (
      <Card sx={{ p: 2.5, height: '100%' }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 750, mb: 0.5 }}>
          {title}
        </Typography>
        <NotAvailable reason="This phase was not run — the requested request count was zero." />
      </Card>
    );
  }

  const throttled = phase.throttled429Count > 0;

  return (
    <Card sx={{ p: 2.5, height: '100%' }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5, flexWrap: 'wrap' }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
          {title}
        </Typography>
        <Chip
          size="small"
          label={throttled ? `${phase.throttled429Count} throttled` : 'none throttled'}
          sx={{
            fontWeight: 750,
            color: throttled ? statusColors.warning : statusColors.success,
            bgcolor: throttled ? `${statusColors.warning}1F` : `${statusColors.success}1F`,
          }}
        />
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
        {description}
      </Typography>

      <Grid container spacing={1.5}>
        <Grid size={{ xs: 6 }}>
          <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
            Requests sent
          </Typography>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            {phase.requestsSent}
          </Typography>
        </Grid>
        <Grid size={{ xs: 6 }}>
          <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
            Achieved rate
          </Typography>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            {phase.observedRequestsPerSec}
            <Typography component="span" variant="caption" color="text.secondary">
              {' '}
              req/s
            </Typography>
          </Typography>
        </Grid>
        <Grid size={{ xs: 6 }}>
          <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
            First throttled at
          </Typography>
          <Typography variant="body1" sx={{ fontWeight: 750 }}>
            {phase.firstThrottledAtRequest ? (
              <Tooltip title="The request number at which the target first answered 429 — the closest observable indication of where its threshold sits.">
                <span style={{ cursor: 'help', color: statusColors.warning }}>request #{phase.firstThrottledAtRequest}</span>
              </Tooltip>
            ) : (
              <Typography component="span" variant="body2" color="text.secondary">
                never
              </Typography>
            )}
          </Typography>
        </Grid>
        <Grid size={{ xs: 6 }}>
          <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
            Avg latency
          </Typography>
          <Typography variant="body1" sx={{ fontWeight: 750 }}>
            {phase.avgLatencyMs} ms
          </Typography>
        </Grid>
      </Grid>

      <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.75, mt: 2 }}>
        {Object.entries(phase.statusDistribution).map(([code, count]) => {
          const numeric = Number(code);
          const color =
            numeric === 0
              ? statusColors.error
              : numeric === 429
                ? statusColors.warning
                : numeric < 400
                  ? statusColors.success
                  : statusColors.error;
          return (
            <Chip
              key={code}
              size="small"
              label={`${numeric === 0 ? 'no response' : code} × ${count}`}
              sx={{ fontWeight: 700, color, bgcolor: `${color}18`, border: `1px solid ${color}3D` }}
            />
          );
        })}
      </Stack>
    </Card>
  );
}

export function RateLimitTab({ defaultUrl }: { defaultUrl?: string }) {
  const { showSuccess, showInfo } = useToast();

  const [targetUrl, setTargetUrl] = useState(defaultUrl ?? '');
  const [httpMethod, setHttpMethod] = useState('GET');
  const [burstRequests, setBurstRequests] = useState(40);
  const [sustainedRequests, setSustainedRequests] = useState(60);
  const [sustainedRps, setSustainedRps] = useState(10);
  const [headersText, setHeadersText] = useState('');
  const [authorized, setAuthorized] = useState(false);

  const [result, setResult] = useState<RateLimitTestResult | null>(null);
  const [isProbing, setIsProbing] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

  const handleProbe = async () => {
    setError(null);
    setIsProbing(true);
    try {
      const probe = await probeRateLimit({
        targetUrl: targetUrl.trim(),
        httpMethod,
        burstRequests,
        sustainedRequests,
        sustainedRequestsPerSecond: sustainedRps,
        headers: parseHeaders(),
        requestBody: null,
        authorizedTarget: authorized,
      });
      setResult(probe);
      if (probe.rateLimitingDetected) {
        showSuccess('Rate limiting confirmed by observation.');
      } else {
        showInfo('No rate-limiting evidence at the load applied — see the verdict for what that does and does not mean.');
      }
    } catch (err) {
      setError(extractErrorMessage(err, 'The rate-limit probe failed.'));
      setResult(null);
    } finally {
      setIsProbing(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Card className="qp-gradient-border" sx={{ p: { xs: 2.5, md: 3 } }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 1 }}>
          <Zap size={22} color={statusColors.warning} />
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            Rate limit probe
          </Typography>
          <Chip size="small" variant="outlined" color="secondary" label="Evidence-based verdict" sx={{ fontWeight: 700 }} />
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5, maxWidth: 820 }}>
          Runs two phases against the target — a fast burst to trip token-bucket limiters, then a paced phase to trip
          window-based ones — and reports only what it observed: HTTP 429 responses, Retry-After values and
          RateLimit-* headers. If nothing is throttled, the verdict says so <em>for the load applied</em>; it never
          concludes that the endpoint is unprotected.
        </Typography>

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 8 }}>
            <TextField
              label="Target endpoint URL"
              fullWidth
              value={targetUrl}
              onChange={(event) => setTargetUrl(event.target.value)}
              disabled={isProbing}
              placeholder="https://staging.example.com/api/login"
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="rl-method">Method</InputLabel>
              <Select labelId="rl-method" value={httpMethod} label="Method" onChange={(event) => setHttpMethod(event.target.value)} disabled={isProbing}>
                {['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'OPTIONS'].map((method) => (
                  <MenuItem key={method} value={method}>
                    {method}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid size={{ xs: 12, sm: 4 }}>
            <Typography variant="caption" sx={{ fontWeight: 750 }}>
              Burst size: {burstRequests} requests
            </Typography>
            <Slider value={burstRequests} onChange={(_, value) => setBurstRequests(value as number)} min={5} max={200} step={5} disabled={isProbing} />
            <Typography variant="caption" color="text.secondary">
              Sent all at once
            </Typography>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Typography variant="caption" sx={{ fontWeight: 750 }}>
              Sustained: {sustainedRequests} requests
            </Typography>
            <Slider value={sustainedRequests} onChange={(_, value) => setSustainedRequests(value as number)} min={0} max={200} step={10} disabled={isProbing} />
            <Typography variant="caption" color="text.secondary">
              0 skips this phase
            </Typography>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Typography variant="caption" sx={{ fontWeight: 750 }}>
              Sustained rate: {sustainedRps} req/s
            </Typography>
            <Slider value={sustainedRps} onChange={(_, value) => setSustainedRps(value as number)} min={1} max={50} disabled={isProbing} />
          </Grid>

          <Grid size={{ xs: 12 }}>
            <TextField
              label="Request headers (optional)"
              fullWidth
              multiline
              minRows={2}
              value={headersText}
              onChange={(event) => setHeadersText(event.target.value)}
              disabled={isProbing}
              placeholder="Authorization: Bearer …"
              helperText="One per line, Name: value. Rate limits are often applied per API key, so include yours to probe the right bucket."
            />
          </Grid>
        </Grid>

        <Alert severity="warning" variant="outlined" icon={<ShieldAlert size={18} />} sx={{ mt: 2.5, borderRadius: 3 }}>
          <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
            A burst probe is deliberately abusive traffic
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            It is only legitimate against infrastructure you control. Running it against a third party may trip their
            abuse protection and get your address blocked.
          </Typography>
          <FormControlLabel
            sx={{ mt: 1 }}
            control={<Checkbox checked={authorized} onChange={(event) => setAuthorized(event.target.checked)} disabled={isProbing} size="small" />}
            label={
              <Typography variant="body2" sx={{ fontWeight: 650 }}>
                I own, or am explicitly authorized to test, this target
              </Typography>
            }
          />
        </Alert>

        <Button
          variant="contained"
          size="large"
          startIcon={isProbing ? <CircularProgress size={16} color="inherit" /> : <Play size={18} />}
          onClick={handleProbe}
          disabled={isProbing || !targetUrl.trim() || !authorized}
          sx={{ mt: 2.5, fontWeight: 750, minWidth: 200 }}
        >
          {isProbing ? 'Probing…' : 'Run probe'}
        </Button>

        {isProbing && (
          <Box sx={{ mt: 2 }}>
            <LinearProgress />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              Sending the burst phase, then the paced sustained phase. This takes roughly{' '}
              {Math.ceil(sustainedRequests / Math.max(1, sustainedRps)) + 3}s.
            </Typography>
          </Box>
        )}

        {error && (
          <Box sx={{ mt: 2 }}>
            <ErrorState title="Probe failed" message={error} onRetry={handleProbe} />
          </Box>
        )}
      </Card>

      {result && (
        <>
          <Card sx={{ p: { xs: 2.5, md: 3 } }}>
            <Alert
              severity={result.rateLimitingDetected ? 'success' : 'info'}
              variant="outlined"
              icon={result.rateLimitingDetected ? <ShieldCheck size={20} /> : <Info size={20} />}
              sx={{ borderRadius: 3 }}
            >
              <AlertTitle sx={{ fontWeight: 800 }}>
                {result.rateLimitingDetected ? 'Rate limiting confirmed' : 'No rate-limiting evidence observed'}
              </AlertTitle>
              <Typography variant="body2">{result.verdict}</Typography>
            </Alert>

            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 2, flexWrap: 'wrap', gap: 1 }}>
              <Chip size="small" variant="outlined" icon={<Timer size={12} />} label={`Probe took ${result.totalDurationMs}ms`} />
              <Chip size="small" variant="outlined" label={`${result.httpMethod} ${result.targetUrl}`} sx={{ maxWidth: 380 }} />
            </Stack>

            {result.notes.length > 0 && (
              <Stack spacing={0.5} sx={{ mt: 1.5 }}>
                {result.notes.map((note, index) => (
                  <Typography key={index} variant="caption" color="text.secondary">
                    • {note}
                  </Typography>
                ))}
              </Stack>
            )}
          </Card>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <PhaseCard
                phase={result.burst}
                title="Burst phase"
                description="All requests fired concurrently, to trip a token-bucket or burst-capacity limiter."
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <PhaseCard
                phase={result.sustained}
                title="Sustained phase"
                description="Requests paced evenly, to trip a fixed- or sliding-window limiter that a short burst can slip under."
              />
            </Grid>
          </Grid>

          <Card sx={{ p: 2.5 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 750, mb: 1.5 }}>
              Header evidence
            </Typography>
            <Box className="qp-scroll-x">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Signal</TableCell>
                    <TableCell>Observed value</TableCell>
                    <TableCell>Interpretation</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow hover>
                    <TableCell sx={{ fontWeight: 700 }}>RateLimit-Limit</TableCell>
                    <TableCell sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem' }}>
                      {result.evidence.rateLimitLimit ?? <NotAvailable reason="The target did not send this header." inline />}
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        {result.evidence.rateLimitLimit
                          ? 'The target advertises its quota, so clients can self-pace.'
                          : 'Not advertised. Clients cannot know the quota without hitting it.'}
                      </Typography>
                    </TableCell>
                  </TableRow>
                  <TableRow hover>
                    <TableCell sx={{ fontWeight: 700 }}>RateLimit-Remaining</TableCell>
                    <TableCell sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem' }}>
                      {result.evidence.rateLimitRemaining ?? <NotAvailable reason="The target did not send this header." inline />}
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        Lets a well-behaved client back off before being throttled.
                      </Typography>
                    </TableCell>
                  </TableRow>
                  <TableRow hover>
                    <TableCell sx={{ fontWeight: 700 }}>Retry-After</TableCell>
                    <TableCell sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem' }}>
                      {result.evidence.retryAfterValues.length > 0 ? (
                        result.evidence.retryAfterValues.join(', ')
                      ) : (
                        <NotAvailable reason="No Retry-After header was returned on any response." inline />
                      )}
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        {result.evidence.retryAfterHonoured
                          ? 'Throttled clients are told when to retry — the correct behaviour.'
                          : 'Throttled clients get no retry guidance and will typically retry immediately, compounding the load.'}
                      </Typography>
                    </TableCell>
                  </TableRow>
                  <TableRow hover>
                    <TableCell sx={{ fontWeight: 700 }}>Headers seen</TableCell>
                    <TableCell colSpan={2}>
                      {result.evidence.allRateLimitHeaderNames.length > 0 ? (
                        <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                          {result.evidence.allRateLimitHeaderNames.map((name) => (
                            <Chip key={name} size="small" variant="outlined" label={name} sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.7rem' }} />
                          ))}
                        </Stack>
                      ) : (
                        <Typography variant="caption" color="text.secondary">
                          No rate-limit-related headers were present on any response.
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </Box>
          </Card>
        </>
      )}

      {!result && !isProbing && !error && (
        <Card>
          <EmptyState
            icon={<Zap size={24} />}
            title="No probe run yet"
            description="Point the probe at an endpoint you own to find out whether it actually enforces rate limiting — and if so, at roughly what threshold."
          />
        </Card>
      )}
    </Stack>
  );
}
