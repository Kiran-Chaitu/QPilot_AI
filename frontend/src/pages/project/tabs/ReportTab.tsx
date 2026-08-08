import { useState } from 'react';
import { Alert, Box, Button, Card, CardContent, Stack, Typography, Grid, Paper } from '@mui/material';
import { FileText, Download, Code, Globe, Sparkles } from 'lucide-react';
import { downloadReport } from '../../../api/analysisApi';
import { httpClient } from '../../../api/httpClient';
import { useToast } from '../../../context/ToastContext';
import { extractErrorMessage } from '../../../api/httpClient';

interface ReportTabProps {
  projectId: number;
  projectName: string;
  hasAnalysis: boolean;
}

export function ReportTab({ projectId, projectName, hasAnalysis }: ReportTabProps) {
  const { showSuccess } = useToast();
  const [isDownloading, setIsDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDownload(format: 'pdf' | 'md' | 'html') {
    setError(null);
    setIsDownloading(true);
    try {
      if (format === 'pdf') {
        const blob = await downloadReport(projectId);
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `QPilot-AI-Quality-Report-${projectId}-${projectName.replace(/\s+/g, '-')}.pdf`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
        showSuccess('PDF Report downloaded successfully!');
      } else {
        // Fetch real report from backend API
        const endpoint = `/projects/${projectId}/report/download/${format}`;
        const { data } = await httpClient.get(endpoint, {
          responseType: 'blob',
        });

        const ext = format === 'md' ? 'md' : 'html';
        const mimeType = format === 'md' ? 'text/markdown' : 'text/html';
        const blob = new Blob([data as BlobPart], { type: `${mimeType};charset=utf-8` });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `QPilot-AI-Report-${projectName.replace(/\s+/g, '-')}.${ext}`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
        showSuccess(`${format.toUpperCase()} Report generated and downloaded!`);
      }
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not generate the report. Please try again.'));
    } finally {
      setIsDownloading(false);
    }
  }

  return (
    <Stack spacing={3}>
      <Card sx={{ p: 4, textAlign: 'center' }}>
        <CardContent sx={{ p: 0 }}>
          <Box sx={{ p: 2, borderRadius: '50%', bgcolor: 'rgba(16, 185, 129, 0.12)', width: 68, height: 68, mx: 'auto', mb: 2, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <FileText size={34} color="#10B981" />
          </Box>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
            Executive Quality & Audit Reports
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 4, maxWidth: 560, mx: 'auto', lineHeight: 1.6 }}>
            Export comprehensive executive summaries, architectural dependency maps, OWASP security vulnerabilities, risk matrices, and generated test suites.
            All reports are generated from your actual analysis data.
          </Typography>

          {!hasAnalysis && (
            <Alert severity="info" icon={<Sparkles size={18} color="#10B981" />} sx={{ mb: 3, maxWidth: 560, mx: 'auto', borderRadius: 2 }}>
              Run AI analysis from the Overview tab first to populate your report with real multi-agent findings.
            </Alert>
          )}

          {error && (
            <Alert severity="error" sx={{ mb: 3, maxWidth: 560, mx: 'auto', borderRadius: 2 }}>
              {error}
            </Alert>
          )}

          <Grid container spacing={2.5} sx={{ maxWidth: 740, mx: 'auto' }}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <Paper sx={{ p: 3, textAlign: 'center', borderRadius: 3, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
                <FileText size={32} color="#EF4444" style={{ marginBottom: 10 }} />
                <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 0.5 }}>
                  PDF Executive Report
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
                  Print-Ready OpenPDF Document
                </Typography>
                <Button
                  fullWidth
                  variant="contained"
                  color="primary"
                  size="small"
                  startIcon={<Download size={14} />}
                  onClick={() => handleDownload('pdf')}
                  disabled={isDownloading || !hasAnalysis}
                  sx={{ fontWeight: 700 }}
                >
                  Download PDF
                </Button>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, sm: 4 }}>
              <Paper sx={{ p: 3, textAlign: 'center', borderRadius: 3, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
                <Code size={32} color="#8B5CF6" style={{ marginBottom: 10 }} />
                <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 0.5 }}>
                  Markdown Spec
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
                  GitHub & Docs Markdown
                </Typography>
                <Button
                  fullWidth
                  variant="outlined"
                  size="small"
                  startIcon={<Download size={14} />}
                  onClick={() => handleDownload('md')}
                  disabled={isDownloading || !hasAnalysis}
                  sx={{ fontWeight: 700 }}
                >
                  Download .MD
                </Button>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, sm: 4 }}>
              <Paper sx={{ p: 3, textAlign: 'center', borderRadius: 3, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
                <Globe size={32} color="#10B981" style={{ marginBottom: 10 }} />
                <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 0.5 }}>
                  HTML Web Dashboard
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
                  Interactive Web Summary
                </Typography>
                <Button
                  fullWidth
                  variant="outlined"
                  size="small"
                  startIcon={<Download size={14} />}
                  onClick={() => handleDownload('html')}
                  disabled={isDownloading || !hasAnalysis}
                  sx={{ fontWeight: 700 }}
                >
                  Download HTML
                </Button>
              </Paper>
            </Grid>
          </Grid>
        </CardContent>
      </Card>
    </Stack>
  );
}
