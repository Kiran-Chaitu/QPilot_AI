import { useState } from 'react';
import { Alert, Box, Button, Card, CardContent, Typography } from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import { downloadReport } from '../../../api/analysisApi';
import { extractErrorMessage } from '../../../api/httpClient';

interface ReportTabProps {
  projectId: number;
  projectName: string;
  hasAnalysis: boolean;
}

export function ReportTab({ projectId, projectName, hasAnalysis }: ReportTabProps) {
  const [isDownloading, setIsDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDownload() {
    setError(null);
    setIsDownloading(true);
    try {
      const blob = await downloadReport(projectId);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `ai-testpilot-report-${projectId}-${projectName.replace(/\s+/g, '-')}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not generate the report. Please try again.'));
    } finally {
      setIsDownloading(false);
    }
  }

  return (
    <Card variant="outlined">
      <CardContent sx={{ textAlign: 'center', py: 6 }}>
        <PictureAsPdfIcon sx={{ fontSize: 56, color: 'error.main', mb: 1 }} />
        <Typography variant="h6">AI Quality Report</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3, maxWidth: 480, mx: 'auto' }}>
          Download a professional PDF containing the project summary, AI code understanding, generated
          tests, security findings, risk score and recommendations.
        </Typography>
        {!hasAnalysis && (
          <Alert severity="info" sx={{ mb: 2, maxWidth: 480, mx: 'auto' }}>
            Run AI analysis from the Overview tab first to include results in the report.
          </Alert>
        )}
        {error && (
          <Alert severity="error" sx={{ mb: 2, maxWidth: 480, mx: 'auto' }}>
            {error}
          </Alert>
        )}
        <Box>
          <Button
            variant="contained"
            size="large"
            startIcon={<DownloadIcon />}
            onClick={handleDownload}
            disabled={isDownloading || !hasAnalysis}
          >
            {isDownloading ? 'Generating…' : 'Download PDF Report'}
          </Button>
        </Box>
      </CardContent>
    </Card>
  );
}
