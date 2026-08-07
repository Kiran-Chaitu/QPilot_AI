import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Divider,
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
  Typography,
} from '@mui/material';
import {
  Globe,
  Play,
  ShieldCheck,
  AlertTriangle,
  ExternalLink,
  Layers,
  Sparkles,
} from 'lucide-react';
import { runWebsiteAudit, type WebsiteAuditResponse } from '../../../api/websiteApi';
import { useToast } from '../../../context/ToastContext';

interface ScoreGaugeProps {
  label: string;
  score: number;
}

function ScoreGauge({ label, score }: ScoreGaugeProps) {
  let color = '#10B981'; // green
  if (score < 50) color = '#EF4444'; // red
  else if (score < 80) color = '#F59E0B'; // orange

  return (
    <Paper
      sx={{
        p: 2.5,
        textAlign: 'center',
        borderRadius: 3,
        border: '1px solid rgba(255, 255, 255, 0.08)',
        background: 'linear-gradient(145deg, rgba(15, 23, 42, 0.6) 0%, rgba(9, 13, 22, 0.8) 100%)',
        backdropFilter: 'blur(12px)',
        transition: 'transform 0.2s ease',
        '&:hover': { transform: 'translateY(-2px)' },
      }}
    >
      <Box sx={{ position: 'relative', display: 'inline-flex', mb: 1 }}>
        <CircularProgress
          variant="determinate"
          value={score}
          size={72}
          thickness={5}
          sx={{ color, strokeLinecap: 'round' }}
        />
        <Box
          sx={{
            top: 0,
            left: 0,
            bottom: 0,
            right: 0,
            position: 'absolute',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Typography variant="h6" component="div" sx={{ fontWeight: 800 }}>
            {score}
          </Typography>
        </Box>
      </Box>
      <Typography variant="body2" sx={{ fontWeight: 700, mt: 0.5 }}>
        {label}
      </Typography>
    </Paper>
  );
}

export function WebsiteAuditorTab({ defaultUrl }: { defaultUrl?: string }) {
  const { showSuccess, showError } = useToast();
  const [url, setUrl] = useState(defaultUrl || 'https://example.com');
  const [isAuditing, setIsAuditing] = useState(false);
  const [auditResult, setAuditResult] = useState<WebsiteAuditResponse | null>(null);

  const handleStartAudit = async () => {
    if (!url) return;
    setIsAuditing(true);
    try {
      const data = await runWebsiteAudit(url);
      setAuditResult(data);
      showSuccess(`Website audit completed for ${data.pageTitle || data.targetUrl}!`);
    } catch {
      showError('Website scan failed. Check URL accessibility.');
    } finally {
      setIsAuditing(false);
    }
  };

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
          <Globe size={24} color="#6366F1" />
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            Synthetic Website Quality & Security Auditor
          </Typography>
          <Chip label="Live Web Crawl Engine" color="primary" size="small" variant="outlined" />
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Perform synthetic web audits to inspect response latencies, security headers (HSTS, CSP, X-Frame-Options), broken links, and WCAG accessibility standards.
        </Typography>

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 9 }}>
            <TextField
              label="Target Website URL"
              fullWidth
              size="small"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://example.com"
              disabled={isAuditing}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 3 }}>
            <Button
              fullWidth
              variant="contained"
              color="primary"
              size="large"
              startIcon={isAuditing ? <CircularProgress size={16} color="inherit" /> : <Play size={18} />}
              onClick={handleStartAudit}
              disabled={isAuditing || !url}
              sx={{ fontWeight: 700, borderRadius: 2 }}
            >
              {isAuditing ? 'Scanning Site…' : 'Run Live Web Audit'}
            </Button>
          </Grid>
        </Grid>

        {isAuditing && (
          <Box sx={{ mt: 3 }}>
            <LinearProgress color="primary" sx={{ height: 6, borderRadius: 3 }} />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              Fetching target URL, evaluating SSL/TLS certificates, auditing HTTP headers, and checking page links...
            </Typography>
          </Box>
        )}
      </Card>

      {/* Results View */}
      {auditResult && (
        <>
          {/* Target Metadata Banner */}
          <Paper sx={{ p: 2.5, borderRadius: 3, border: '1px solid rgba(255, 255, 255, 0.08)' }}>
            <Stack direction="row" spacing={3} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  PAGE TITLE
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 800, color: 'primary.main' }}>
                  {auditResult.pageTitle}
                </Typography>
              </Box>
              <Divider orientation="vertical" flexItem />
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  RESPONSE LATENCY
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 800, color: 'success.main' }}>
                  {auditResult.responseTimeMs} ms
                </Typography>
              </Box>
              <Divider orientation="vertical" flexItem />
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  HTTP STATUS CODE
                </Typography>
                <Chip label={auditResult.responseCode} color="success" size="small" sx={{ fontWeight: 700 }} />
              </Box>
            </Stack>
          </Paper>

          {/* Lighthouse Score Gauges */}
          <Grid container spacing={2.5}>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <ScoreGauge label="Performance" score={auditResult.performanceScore} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <ScoreGauge label="Accessibility" score={auditResult.accessibilityScore} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <ScoreGauge label="Best Practices" score={auditResult.bestPracticesScore} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <ScoreGauge label="SEO Standard" score={auditResult.seoScore} />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <ScoreGauge label="Security Headers" score={auditResult.securityScore} />
            </Grid>
          </Grid>

          {/* Audit Details */}
          <Grid container spacing={3}>
            {/* Security Headers Table */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Card sx={{ p: 3, height: '100%' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                  <ShieldCheck size={20} color="#10B981" />
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    Security Headers Status
                  </Typography>
                </Box>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Header Name</TableCell>
                      <TableCell>Configured Value</TableCell>
                      <TableCell>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {auditResult.headers.map((h) => (
                      <TableRow key={h.name}>
                        <TableCell sx={{ fontWeight: 600 }}>{h.name}</TableCell>
                        <TableCell sx={{ fontFamily: 'JetBrains Mono', fontSize: 11 }}>
                          <code>{h.value}</code>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={h.status}
                            color={h.present ? 'success' : 'warning'}
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

            {/* Extracted Links & Health */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Card sx={{ p: 3, height: '100%' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                  <Layers size={20} color="#6366F1" />
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    Page Links & Health Checks
                  </Typography>
                </Box>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Link URL</TableCell>
                      <TableCell>HTTP Status</TableCell>
                      <TableCell>Latency</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {auditResult.links.map((l) => (
                      <TableRow key={l.url}>
                        <TableCell sx={{ fontSize: 12, fontFamily: 'JetBrains Mono' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                            {l.url}
                            <ExternalLink size={12} style={{ opacity: 0.6 }} />
                          </Box>
                        </TableCell>
                        <TableCell>
                          <Chip label={l.statusCode} color="success" size="small" sx={{ height: 20 }} />
                        </TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>{l.responseTimeMs} ms</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Card>
            </Grid>
          </Grid>

          {/* Recommendations */}
          {auditResult.recommendations.length > 0 && (
            <Card sx={{ p: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <Sparkles size={20} color="#F59E0B" />
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  AI Optimization Recommendations
                </Typography>
              </Box>
              <Stack spacing={1.5}>
                {auditResult.recommendations.map((rec, i) => (
                  <Alert key={i} severity="warning" icon={<AlertTriangle size={18} />} sx={{ borderRadius: 2 }}>
                    {rec}
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
