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
  Divider,
  FormControlLabel,
  Grid,
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
  AlertTriangle,
  CheckCircle2,
  Cookie,
  ExternalLink,
  Globe,
  Info,
  Layers,
  Link2,
  Lock,
  Play,
  Search,
  ShieldCheck,
  Wrench,
  XCircle,
} from 'lucide-react';
import { runWebsiteAudit, type WebsiteAuditResponse } from '../../../api/websiteApi';
import { extractErrorMessage } from '../../../api/httpClient';
import { useToast } from '../../../context/ToastContext';
import { EmptyState, ErrorState, NotAvailable } from '../../../components/common/StateViews';
import { brand, severityColors, status as statusColors } from '../../../theme/palette';

/**
 * Renders one category score.
 *
 * <p>A null score renders as an explicit "Not available" rather than as 0. That distinction is the whole
 * point: zero is a measurement of failure, while null means nothing could be measured, and conflating
 * them is exactly how a broken audit ends up looking like a bad website.
 */
function ScoreDial({
  label,
  score,
  breakdown,
}: {
  label: string;
  score?: number;
  breakdown?: string[];
}) {
  if (score === null || score === undefined) {
    return (
      <Paper sx={{ p: 2, borderRadius: 3, border: '1px solid', borderColor: 'divider', textAlign: 'center', height: '100%' }}>
        <Box sx={{ height: 72, display: 'grid', placeItems: 'center' }}>
          <NotAvailable reason={`${label} could not be measured for this target. See "Checks not performed" below.`} />
        </Box>
        <Typography variant="body2" sx={{ fontWeight: 700, mt: 0.5 }}>
          {label}
        </Typography>
      </Paper>
    );
  }

  const color = score >= 80 ? statusColors.success : score >= 50 ? statusColors.warning : statusColors.error;

  return (
    <Tooltip
      title={
        breakdown && breakdown.length > 0 ? (
          <Box>
            {breakdown.map((line, index) => (
              <Typography key={index} variant="caption" sx={{ display: 'block', mb: 0.25 }}>
                {line}
              </Typography>
            ))}
          </Box>
        ) : (
          `${label}: ${score}/100`
        )
      }
    >
      <Paper
        className="qp-lift"
        sx={{
          p: 2,
          borderRadius: 3,
          border: '1px solid',
          borderColor: 'divider',
          textAlign: 'center',
          cursor: 'help',
          height: '100%',
        }}
      >
        <Box sx={{ position: 'relative', display: 'inline-flex', mb: 0.5 }}>
          <CircularProgress variant="determinate" value={100} size={72} thickness={4} sx={{ color: 'divider' }} />
          <CircularProgress
            variant="determinate"
            value={score}
            size={72}
            thickness={4}
            sx={{ color, position: 'absolute', left: 0, '& .MuiCircularProgress-circle': { strokeLinecap: 'round' } }}
          />
          <Box sx={{ inset: 0, position: 'absolute', display: 'grid', placeItems: 'center' }}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              {score}
            </Typography>
          </Box>
        </Box>
        <Typography variant="body2" sx={{ fontWeight: 700 }}>
          {label}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          hover for breakdown
        </Typography>
      </Paper>
    </Tooltip>
  );
}

function Fact({ label, value, mono }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <Box>
      <Typography variant="overline" color="text.secondary" sx={{ display: 'block', lineHeight: 1.5 }}>
        {label}
      </Typography>
      <Typography
        variant="body2"
        sx={{ fontWeight: 700, fontFamily: mono ? 'var(--font-mono)' : undefined, overflowWrap: 'anywhere' }}
      >
        {value}
      </Typography>
    </Box>
  );
}

export function WebsiteAuditorTab({ defaultUrl }: { defaultUrl?: string }) {
  const { showSuccess, showWarning } = useToast();
  // Deliberately not pre-filled with a sample domain: auditing example.com by accident produces a
  // meaningless report that looks real.
  const [url, setUrl] = useState(defaultUrl ?? '');
  const [checkLinks, setCheckLinks] = useState(false);
  const [isAuditing, setIsAuditing] = useState(false);
  const [result, setResult] = useState<WebsiteAuditResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleAudit = async () => {
    if (!url.trim()) return;
    setIsAuditing(true);
    setError(null);
    try {
      const audit = await runWebsiteAudit(url.trim(), checkLinks);
      setResult(audit);
      if (audit.reachable) {
        showSuccess(`Audit complete — HTTP ${audit.responseCode} in ${audit.responseTimeMs}ms.`);
      } else {
        showWarning('Audit complete, but the target could not be reached. See the report for the reason.');
      }
    } catch (err) {
      setError(extractErrorMessage(err, 'The audit request failed.'));
      setResult(null);
    } finally {
      setIsAuditing(false);
    }
  };

  const brokenLinks = result?.links.filter((link) => link.broken) ?? [];

  return (
    <Stack spacing={3}>
      {/* ── Input ─────────────────────────────────────────────────────── */}
      <Card className="qp-gradient-border" sx={{ p: { xs: 2.5, md: 3 } }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 1 }}>
          <Globe size={22} color={brand.secondary} />
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            Live website audit
          </Typography>
          <Chip size="small" variant="outlined" color="secondary" label="Measured, not estimated" sx={{ fontWeight: 700 }} />
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5, maxWidth: 800 }}>
          Fetches the URL and reports what the response actually contained: timings, TLS certificate, redirect chain,
          security headers, cookie flags, and the SEO/accessibility properties detectable from HTML. Anything that
          cannot be measured is reported as unavailable, with the reason.
        </Typography>

        <Grid container spacing={2} sx={{ alignItems: 'flex-start' }}>
          <Grid size={{ xs: 12, md: 7 }}>
            <TextField
              label="Website URL"
              fullWidth
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://your-site.example.com"
              disabled={isAuditing}
              onKeyDown={(event) => {
                if (event.key === 'Enter') handleAudit();
              }}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 3 }}>
            <FormControlLabel
              control={<Checkbox checked={checkLinks} onChange={(event) => setCheckLinks(event.target.checked)} disabled={isAuditing} size="small" />}
              label={
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 650 }}>
                    Check links
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Requests each link found (slower)
                  </Typography>
                </Box>
              }
            />
          </Grid>
          <Grid size={{ xs: 12, md: 2 }}>
            <Button
              fullWidth
              variant="contained"
              size="large"
              startIcon={isAuditing ? <CircularProgress size={16} color="inherit" /> : <Play size={18} />}
              onClick={handleAudit}
              disabled={isAuditing || !url.trim()}
              sx={{ fontWeight: 750 }}
            >
              {isAuditing ? 'Auditing…' : 'Run audit'}
            </Button>
          </Grid>
        </Grid>

        {isAuditing && (
          <Box sx={{ mt: 2.5 }}>
            <LinearProgress />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              Resolving DNS, fetching the page, inspecting the TLS session and parsing the returned HTML…
            </Typography>
          </Box>
        )}

        {error && (
          <Box sx={{ mt: 2 }}>
            <ErrorState title="Audit request failed" message={error} onRetry={handleAudit} />
          </Box>
        )}
      </Card>

      {/* ── Unreachable target: a real finding, rendered as a real report ── */}
      {result && !result.reachable && (
        <Card sx={{ p: { xs: 2.5, md: 3 } }}>
          <Alert severity="error" variant="outlined" icon={<XCircle size={20} />} sx={{ borderRadius: 3 }}>
            <AlertTitle sx={{ fontWeight: 800 }}>Target could not be reached</AlertTitle>
            <Typography variant="body2" sx={{ mb: 1.5 }}>
              {result.failureReason}
            </Typography>
            <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
              <Chip size="small" variant="outlined" label={`Target: ${result.targetUrl}`} />
              {result.dnsLookupMs !== undefined && result.dnsLookupMs !== null && (
                <Chip size="small" variant="outlined" label={`DNS lookup: ${result.dnsLookupMs}ms`} />
              )}
              <Chip size="small" variant="outlined" label={`Audit took ${result.auditDurationMs}ms`} />
            </Stack>
          </Alert>
          <Alert severity="info" variant="outlined" sx={{ mt: 2, borderRadius: 3 }} icon={<Info size={18} />}>
            <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
              No scores are shown, by design
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Nothing could be measured, so reporting scores of zero would be inventing a result. Fix the URL or bring
              the service up, then run the audit again.
            </Typography>
          </Alert>
          <Button variant="contained" onClick={handleAudit} sx={{ mt: 2, fontWeight: 750 }} startIcon={<Play size={16} />}>
            Retry audit
          </Button>
        </Card>
      )}

      {/* ── Reachable target: full report ─────────────────────────────── */}
      {result?.reachable && (
        <>
          <Card sx={{ p: { xs: 2, md: 2.5 } }}>
            <Grid container spacing={2.5}>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Fact
                  label="HTTP status"
                  value={
                    <Chip
                      size="small"
                      label={`${result.responseCode} ${result.responseStatusText ?? ''}`}
                      sx={{
                        fontWeight: 750,
                        color: (result.responseCode ?? 0) < 400 ? statusColors.success : statusColors.error,
                        bgcolor: (result.responseCode ?? 0) < 400 ? `${statusColors.success}1F` : `${statusColors.error}1F`,
                      }}
                    />
                  }
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Fact
                  label="Server response time"
                  value={`${result.responseTimeMs} ms`}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Fact label="DNS lookup" value={result.dnsLookupMs !== undefined ? `${result.dnsLookupMs} ms` : <NotAvailable reason="DNS timing was not captured." />} />
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Fact label="Protocol" value={`${result.httpProtocolVersion ?? '—'} · ${result.httpsEnabled ? 'HTTPS' : 'HTTP (insecure)'}`} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Fact label="Page title" value={result.page?.title ?? <NotAvailable reason="The returned HTML contains no <title> element." />} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Fact label="Final URL" value={result.finalUrl ?? result.targetUrl} mono />
              </Grid>
            </Grid>

            {result.redirectChain.length > 0 && (
              <>
                <Divider sx={{ my: 2 }} />
                <Typography variant="overline" color="text.secondary">
                  Redirect chain ({result.redirectChain.length} hop{result.redirectChain.length === 1 ? '' : 's'})
                </Typography>
                <Stack spacing={0.5} sx={{ mt: 0.75 }}>
                  {result.redirectChain.map((hop) => (
                    <Typography key={hop.hop} variant="caption" sx={{ fontFamily: 'var(--font-mono)', overflowWrap: 'anywhere' }}>
                      {hop.hop}. HTTP {hop.statusCode} — {hop.url} → {hop.location}
                    </Typography>
                  ))}
                </Stack>
              </>
            )}
          </Card>

          <Grid container spacing={2} className="qp-stagger">
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <ScoreDial label="Security" score={result.scores.security} breakdown={result.scores.breakdown?.security} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <ScoreDial label="SEO" score={result.scores.seo} breakdown={result.scores.breakdown?.seo} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <ScoreDial label="Accessibility" score={result.scores.accessibility} breakdown={result.scores.breakdown?.accessibility} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <ScoreDial label="Best practices" score={result.scores.bestPractices} breakdown={result.scores.breakdown?.bestPractices} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <ScoreDial label="Response speed" score={result.scores.performance} breakdown={result.scores.breakdown?.performance} />
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            {/* Security headers */}
            <Grid size={{ xs: 12, lg: 6 }}>
              <Card sx={{ p: 2.5, height: '100%' }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                  <ShieldCheck size={18} color={statusColors.success} />
                  <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                    Security headers
                  </Typography>
                </Stack>
                <Box className="qp-scroll-x">
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Header</TableCell>
                        <TableCell>Assessment</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {result.securityHeaders.map((header) => {
                        const color =
                          header.severity === 'OK'
                            ? statusColors.success
                            : header.severity === 'INFO'
                              ? statusColors.info
                              : severityColors[header.severity] ?? statusColors.warning;
                        return (
                          <TableRow key={header.name} hover>
                            <TableCell sx={{ verticalAlign: 'top', minWidth: 190 }}>
                              <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                                {header.present ? <CheckCircle2 size={14} color={statusColors.success} /> : <AlertTriangle size={14} color={color} />}
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  {header.name}
                                </Typography>
                              </Stack>
                              {header.value && (
                                <Typography
                                  variant="caption"
                                  sx={{ fontFamily: 'var(--font-mono)', display: 'block', mt: 0.5, opacity: 0.75, overflowWrap: 'anywhere' }}
                                >
                                  {header.value.length > 90 ? `${header.value.slice(0, 90)}…` : header.value}
                                </Typography>
                              )}
                            </TableCell>
                            <TableCell sx={{ verticalAlign: 'top' }}>
                              <Typography variant="caption" sx={{ color: header.present ? 'text.secondary' : color }}>
                                {header.assessment}
                              </Typography>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </Box>
              </Card>
            </Grid>

            {/* TLS + technologies */}
            <Grid size={{ xs: 12, lg: 6 }}>
              <Stack spacing={2} sx={{ height: '100%' }}>
                <Card sx={{ p: 2.5 }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                    <Lock size={18} color={brand.secondary} />
                    <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                      TLS certificate
                    </Typography>
                  </Stack>
                  {result.tls ? (
                    <Grid container spacing={2}>
                      <Grid size={{ xs: 6 }}>
                        <Fact label="Protocol" value={result.tls.protocol ?? <NotAvailable reason="Not exposed by the TLS session." />} />
                      </Grid>
                      <Grid size={{ xs: 6 }}>
                        <Fact
                          label="Expires in"
                          value={
                            result.tls.daysUntilExpiry !== undefined && result.tls.daysUntilExpiry !== null ? (
                              <Box
                                component="span"
                                sx={{
                                  color:
                                    result.tls.daysUntilExpiry < 0
                                      ? statusColors.error
                                      : result.tls.daysUntilExpiry < 30
                                        ? statusColors.warning
                                        : statusColors.success,
                                }}
                              >
                                {result.tls.daysUntilExpiry < 0
                                  ? `expired ${Math.abs(result.tls.daysUntilExpiry)} days ago`
                                  : `${result.tls.daysUntilExpiry} days`}
                              </Box>
                            ) : (
                              <NotAvailable reason="Certificate validity could not be read." />
                            )
                          }
                        />
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <Fact label="Cipher suite" value={result.tls.cipherSuite ?? <NotAvailable reason="Not exposed." />} mono />
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <Fact label="Issuer" value={result.tls.certificateIssuer ?? <NotAvailable reason="Not exposed." />} mono />
                      </Grid>
                      {result.tls.certificateError && (
                        <Grid size={{ xs: 12 }}>
                          <Alert severity="error" variant="outlined" sx={{ borderRadius: 2.5 }}>
                            <Typography variant="caption">{result.tls.certificateError}</Typography>
                          </Alert>
                        </Grid>
                      )}
                    </Grid>
                  ) : (
                    <NotAvailable reason="No TLS session was established — the final URL uses plain HTTP, or the handshake details were unavailable." />
                  )}
                </Card>

                <Card sx={{ p: 2.5, flexGrow: 1 }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                    <Wrench size={18} />
                    <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                      Detected technologies
                    </Typography>
                  </Stack>
                  <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.75 }}>
                    {result.detectedTechnologies.map((tech) => (
                      <Chip key={tech} size="small" variant="outlined" label={tech} />
                    ))}
                  </Stack>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
                    Identified from response headers and markup fingerprints the frameworks themselves emit.
                  </Typography>
                </Card>
              </Stack>
            </Grid>

            {/* SEO */}
            <Grid size={{ xs: 12, lg: 6 }}>
              <Card sx={{ p: 2.5, height: '100%' }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                  <Search size={18} />
                  <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                    SEO
                  </Typography>
                </Stack>
                {result.seo ? (
                  <>
                    <Grid container spacing={1.5} sx={{ mb: 1.5 }}>
                      {[
                        ['Title', result.seo.hasTitle],
                        ['Meta description', result.seo.hasMetaDescription],
                        ['Single H1', result.seo.hasSingleH1],
                        ['Canonical link', result.seo.hasCanonical],
                        ['Open Graph', result.seo.hasOpenGraph],
                        ['Structured data', result.seo.hasStructuredData],
                      ].map(([label, present]) => (
                        <Grid size={{ xs: 6 }} key={String(label)}>
                          <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                            {present ? <CheckCircle2 size={14} color={statusColors.success} /> : <XCircle size={14} color={statusColors.warning} />}
                            <Typography variant="body2">{label}</Typography>
                          </Stack>
                        </Grid>
                      ))}
                    </Grid>
                    {result.seo.issues.length > 0 && (
                      <Stack spacing={0.75}>
                        {result.seo.issues.map((issue, index) => (
                          <Typography key={index} variant="caption" color="text.secondary">
                            • {issue}
                          </Typography>
                        ))}
                      </Stack>
                    )}
                  </>
                ) : (
                  <NotAvailable reason="No HTML was returned, so SEO properties could not be read." />
                )}
              </Card>
            </Grid>

            {/* Accessibility */}
            <Grid size={{ xs: 12, lg: 6 }}>
              <Card sx={{ p: 2.5, height: '100%' }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                  <Layers size={18} />
                  <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                    Accessibility
                  </Typography>
                </Stack>
                {result.accessibility ? (
                  <>
                    <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1, mb: 1.5 }}>
                      <Chip
                        size="small"
                        variant="outlined"
                        label={`Images without alt: ${result.accessibility.imagesMissingAlt ?? 0} / ${result.accessibility.totalImages ?? 0}`}
                      />
                      <Chip
                        size="small"
                        variant="outlined"
                        label={`Inputs without label: ${result.accessibility.inputsMissingLabel ?? 0} / ${result.accessibility.totalInputs ?? 0}`}
                      />
                      <Chip size="small" variant="outlined" label={`lang attribute: ${result.accessibility.hasLangAttribute ? 'set' : 'missing'}`} />
                    </Stack>
                    {result.accessibility.issues.length > 0 && (
                      <Stack spacing={0.75} sx={{ mb: 1.5 }}>
                        {result.accessibility.issues.map((issue, index) => (
                          <Typography key={index} variant="caption" color="text.secondary">
                            • {issue}
                          </Typography>
                        ))}
                      </Stack>
                    )}
                    <Alert severity="info" variant="outlined" sx={{ borderRadius: 2.5 }} icon={<Info size={16} />}>
                      <Typography variant="caption">{result.accessibility.note}</Typography>
                    </Alert>
                  </>
                ) : (
                  <NotAvailable reason="No HTML was returned, so accessibility structure could not be checked." />
                )}
              </Card>
            </Grid>

            {/* Cookies */}
            {result.cookies.length > 0 && (
              <Grid size={{ xs: 12, lg: 6 }}>
                <Card sx={{ p: 2.5, height: '100%' }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                    <Cookie size={18} />
                    <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                      Cookies ({result.cookies.length})
                    </Typography>
                  </Stack>
                  <Box className="qp-scroll-x">
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Name</TableCell>
                          <TableCell>Secure</TableCell>
                          <TableCell>HttpOnly</TableCell>
                          <TableCell>SameSite</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {result.cookies.map((cookie) => (
                          <Tooltip key={cookie.name} title={cookie.assessment}>
                            <TableRow hover sx={{ cursor: 'help' }}>
                              <TableCell sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.78rem' }}>{cookie.name}</TableCell>
                              <TableCell>{cookie.secure ? <CheckCircle2 size={14} color={statusColors.success} /> : <XCircle size={14} color={statusColors.error} />}</TableCell>
                              <TableCell>{cookie.httpOnly ? <CheckCircle2 size={14} color={statusColors.success} /> : <XCircle size={14} color={statusColors.error} />}</TableCell>
                              <TableCell>{cookie.sameSite ?? '—'}</TableCell>
                            </TableRow>
                          </Tooltip>
                        ))}
                      </TableBody>
                    </Table>
                  </Box>
                </Card>
              </Grid>
            )}

            {/* Links */}
            {result.links.length > 0 && (
              <Grid size={{ xs: 12, lg: result.cookies.length > 0 ? 6 : 12 }}>
                <Card sx={{ p: 2.5, height: '100%' }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                    <Link2 size={18} />
                    <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                      Links checked ({result.links.length})
                    </Typography>
                    {brokenLinks.length > 0 && (
                      <Chip size="small" color="error" label={`${brokenLinks.length} broken`} sx={{ fontWeight: 750 }} />
                    )}
                  </Stack>
                  <Box className="qp-scroll-x" sx={{ maxHeight: 320, overflowY: 'auto' }}>
                    <Table size="small" stickyHeader>
                      <TableHead>
                        <TableRow>
                          <TableCell>URL</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell align="right">Latency</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {result.links.map((link) => (
                          <TableRow key={link.url} hover>
                            <TableCell sx={{ maxWidth: 340 }}>
                              <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', minWidth: 0 }}>
                                <Typography variant="caption" className="qp-truncate" sx={{ fontFamily: 'var(--font-mono)' }}>
                                  {link.url}
                                </Typography>
                                <ExternalLink size={11} style={{ opacity: 0.5, flexShrink: 0 }} />
                              </Stack>
                            </TableCell>
                            <TableCell>
                              {link.statusCode !== undefined && link.statusCode !== null ? (
                                <Chip
                                  size="small"
                                  label={link.statusCode}
                                  sx={{
                                    fontWeight: 750,
                                    color: link.broken ? statusColors.error : statusColors.success,
                                    bgcolor: link.broken ? `${statusColors.error}1F` : `${statusColors.success}1F`,
                                  }}
                                />
                              ) : (
                                <Tooltip title={link.error ?? 'Request failed'}>
                                  <Chip size="small" label="No response" sx={{ fontWeight: 750, color: statusColors.error, bgcolor: `${statusColors.error}1F`, cursor: 'help' }} />
                                </Tooltip>
                              )}
                            </TableCell>
                            <TableCell align="right">
                              <Typography variant="caption">{link.responseTimeMs !== undefined ? `${link.responseTimeMs} ms` : '—'}</Typography>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </Box>
                </Card>
              </Grid>
            )}
          </Grid>

          {/* Recommendations */}
          {result.recommendations.length > 0 && (
            <Card sx={{ p: 2.5 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 750, mb: 1.5 }}>
                Recommendations ({result.recommendations.length})
              </Typography>
              <Stack spacing={1.25}>
                {result.recommendations.map((recommendation, index) => {
                  const color = severityColors[recommendation.severity] ?? statusColors.info;
                  return (
                    <Paper
                      key={index}
                      sx={{ p: 1.75, borderRadius: 2.5, border: '1px solid', borderColor: 'divider', borderLeft: `3px solid ${color}` }}
                    >
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5, flexWrap: 'wrap' }}>
                        <Chip size="small" label={recommendation.severity} sx={{ fontWeight: 750, color, bgcolor: `${color}1F` }} />
                        <Chip size="small" variant="outlined" label={recommendation.category.replace(/_/g, ' ')} />
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {recommendation.title}
                        </Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">
                        {recommendation.detail}
                      </Typography>
                    </Paper>
                  );
                })}
              </Stack>
            </Card>
          )}

          {/* What was not measured — stated rather than omitted. */}
          {result.unavailableChecks.length > 0 && (
            <Card sx={{ p: 2.5 }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
                <Info size={18} />
                <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                  Checks not performed
                </Typography>
              </Stack>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                The absence of findings in these areas is not evidence that no issues exist.
              </Typography>
              <Stack spacing={1}>
                {result.unavailableChecks.map((check, index) => (
                  <Typography key={index} variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    • {check}
                  </Typography>
                ))}
              </Stack>
            </Card>
          )}
        </>
      )}

      {!result && !isAuditing && !error && (
        <Card>
          <EmptyState
            icon={<Globe size={24} />}
            title="No audit run yet"
            description="Enter a URL above to fetch it and see its real response timings, TLS details, security headers, and SEO/accessibility properties."
          />
        </Card>
      )}
    </Stack>
  );
}
