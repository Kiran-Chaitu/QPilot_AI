import { useState } from 'react';
import { Alert, Box, Button, Card, CircularProgress, Grid, Paper, Stack, Typography } from '@mui/material';
import { Code, Download, FileText, Globe, Info, Sparkles } from 'lucide-react';
import { downloadReport, type ReportFormat } from '../../../api/analysisApi';
import { extractBlobErrorMessage } from '../../../api/httpClient';
import { useToast } from '../../../context/ToastContext';
import { ErrorState } from '../../../components/common/StateViews';
import { brand, status as statusColors } from '../../../theme/palette';

const FORMATS: Array<{ format: ReportFormat; label: string; sub: string; icon: typeof FileText; color: string; extension: string }> = [
  { format: 'pdf', label: 'PDF report', sub: 'Print-ready, for stakeholders', icon: FileText, color: statusColors.error, extension: 'pdf' },
  { format: 'md', label: 'Markdown', sub: 'For repositories and wikis', icon: Code, color: brand.primary, extension: 'md' },
  { format: 'html', label: 'HTML', sub: 'Self-contained web page', icon: Globe, color: brand.secondary, extension: 'html' },
];

export function ReportTab({
  projectId,
  projectName,
  hasAnalysis,
}: {
  projectId: number;
  projectName: string;
  hasAnalysis: boolean;
}) {
  const { showSuccess } = useToast();
  const [downloading, setDownloading] = useState<ReportFormat | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleDownload = async (format: ReportFormat, extension: string) => {
    setError(null);
    setDownloading(format);
    try {
      const blob = await downloadReport(projectId, format);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      const safeName = projectName.replace(/[^A-Za-z0-9_-]+/g, '-');
      link.download = `QPilot-report-${safeName}.${extension}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      showSuccess(`${format.toUpperCase()} report downloaded.`);
    } catch (err) {
      // Download errors arrive as a Blob containing JSON, so the message has to be read
      // asynchronously — otherwise the user sees "[object Object]" instead of the actual reason.
      setError(await extractBlobErrorMessage(err, 'The report could not be generated.'));
    } finally {
      setDownloading(null);
    }
  };

  return (
    <Stack spacing={2.5}>
      <Card sx={{ p: { xs: 2.5, md: 4 } }}>
        <Stack spacing={1} sx={{ alignItems: 'center', textAlign: 'center', mb: 3 }}>
          <Box
            sx={{
              width: 60,
              height: 60,
              borderRadius: '50%',
              display: 'grid',
              placeItems: 'center',
              bgcolor: 'rgba(124, 92, 255, 0.12)',
              border: '1px solid rgba(124, 92, 255, 0.3)',
            }}
          >
            <FileText size={28} color={brand.primary} />
          </Box>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            Export the quality report
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 620 }}>
            Every report is generated from this project&apos;s stored results at the moment you download it. Measured
            findings and AI suggestions are kept in separate sections, test counts are broken out by real execution
            status, and the risk score is printed with the arithmetic behind it.
          </Typography>
        </Stack>

        {!hasAnalysis && (
          <Alert severity="info" variant="outlined" icon={<Sparkles size={18} />} sx={{ mb: 3, borderRadius: 3 }}>
            <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
              Nothing to report yet
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Run an analysis on this project first. QPilot will not produce a report from an empty result set — an
              empty report would imply a clean bill of health nobody measured.
            </Typography>
          </Alert>
        )}

        {error && (
          <Box sx={{ mb: 3 }}>
            <ErrorState title="Report generation failed" message={error} />
          </Box>
        )}

        <Grid container spacing={2} sx={{ maxWidth: 860, mx: 'auto' }} className="qp-stagger">
          {FORMATS.map(({ format, label, sub, icon: Icon, color, extension }) => (
            <Grid size={{ xs: 12, sm: 4 }} key={format}>
              <Paper
                className="qp-lift"
                sx={{
                  p: 3,
                  textAlign: 'center',
                  borderRadius: 3,
                  border: '1px solid',
                  borderColor: 'divider',
                  bgcolor: 'action.hover',
                  height: '100%',
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Icon size={30} color={color} style={{ margin: '0 auto 10px' }} />
                <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 0.25 }}>
                  {label}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2, flexGrow: 1 }}>
                  {sub}
                </Typography>
                <Button
                  fullWidth
                  variant={format === 'pdf' ? 'contained' : 'outlined'}
                  size="small"
                  startIcon={downloading === format ? <CircularProgress size={13} color="inherit" /> : <Download size={14} />}
                  onClick={() => handleDownload(format, extension)}
                  disabled={downloading !== null || !hasAnalysis}
                  sx={{ fontWeight: 750 }}
                >
                  {downloading === format ? 'Generating…' : `Download`}
                </Button>
              </Paper>
            </Grid>
          ))}
        </Grid>
      </Card>

      <Alert severity="info" variant="outlined" icon={<Info size={18} />} sx={{ borderRadius: 3 }}>
        <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
          What the report deliberately includes
        </Typography>
        <Typography variant="caption" color="text.secondary">
          A &quot;Checks not performed&quot; section, listing what QPilot could not assess and why. Reports get forwarded to
          people who will not re-check the app, so a section with no findings must not be mistakable for a section that
          was never measured.
        </Typography>
      </Alert>
    </Stack>
  );
}
