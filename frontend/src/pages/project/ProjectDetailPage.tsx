import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Alert, Box, CircularProgress, Stack, Tab, Tabs, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import IconButton from '@mui/material/IconButton';
import { AppLayout } from '../../components/layout/AppLayout';
import { StatusChip } from '../../components/common/StatusChip';
import { getProjectDetail } from '../../api/projectApi';
import { getLatestAnalysis, runAnalysis } from '../../api/analysisApi';
import { extractErrorMessage } from '../../api/httpClient';
import type { ProjectDetailResponse } from '../../types/project';
import type { AnalysisResultResponse } from '../../types/analysis';
import { OverviewTab } from './tabs/OverviewTab';
import { GeneratedTestsTab } from './tabs/GeneratedTestsTab';
import { SecurityReportTab } from './tabs/SecurityReportTab';
import { RiskCoverageTab } from './tabs/RiskCoverageTab';
import { ReportTab } from './tabs/ReportTab';

const TABS = ['Overview', 'Generated Tests', 'Security Report', 'Risk & Coverage', 'Report'] as const;
const EXTRACTING_POLL_INTERVAL_MS = 2000;

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const navigate = useNavigate();

  const [detail, setDetail] = useState<ProjectDetailResponse | null>(null);
  const [analysis, setAnalysis] = useState<AnalysisResultResponse | null>(null);
  const [activeTab, setActiveTab] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [analysisError, setAnalysisError] = useState<string | null>(null);
  const pollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const loadProject = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getProjectDetail(projectId);
      setDetail(data);
      // Background extraction/analysis (chunked-upload projects) hasn't produced a structure
      // summary yet; there's nothing meaningful to show in the AI Analysis panel until it does.
      if (data.project.status !== 'EXTRACTING') {
        try {
          const latest = await getLatestAnalysis(projectId);
          setAnalysis(latest);
        } catch {
          setAnalysis(null);
        }
      }
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load this project.'));
    } finally {
      setIsLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    loadProject();
  }, [loadProject]);

  // While a chunked-upload project is still being extracted/structurally analyzed in the
  // background, poll for status changes instead of showing stale/empty tabs.
  useEffect(() => {
    if (detail?.project.status !== 'EXTRACTING') {
      return;
    }
    pollTimeoutRef.current = setTimeout(async () => {
      try {
        const data = await getProjectDetail(projectId);
        setDetail(data);
      } catch {
        // Transient polling errors are ignored; the next tick will retry.
      }
    }, EXTRACTING_POLL_INTERVAL_MS);
    return () => {
      if (pollTimeoutRef.current) {
        clearTimeout(pollTimeoutRef.current);
      }
    };
  }, [detail?.project.status, projectId]);

  async function handleAnalyze() {
    setIsAnalyzing(true);
    setAnalysisError(null);
    try {
      const result = await runAnalysis(projectId);
      setAnalysis(result);
      const refreshedDetail = await getProjectDetail(projectId);
      setDetail(refreshedDetail);
    } catch (err) {
      setAnalysisError(extractErrorMessage(err, 'AI analysis failed. Please try again.'));
    } finally {
      setIsAnalyzing(false);
    }
  }

  if (isLoading) {
    return (
      <AppLayout>
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      </AppLayout>
    );
  }

  if (error || !detail) {
    return (
      <AppLayout>
        <Alert severity="error">{error ?? 'Project not found.'}</Alert>
      </AppLayout>
    );
  }

  const headerRow = (
    <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: 'center' }}>
      <IconButton onClick={() => navigate('/dashboard')} size="small">
        <ArrowBackIcon />
      </IconButton>
      <Typography variant="h4">{detail.project.name}</Typography>
    </Stack>
  );

  // Still extracting/structurally analyzing in the background (see the chunked-upload pipeline):
  // there is no structure summary yet, so show a processing state instead of the tabs (which
  // expect a non-null structure) rather than crash on missing data.
  if (detail.project.status === 'EXTRACTING' || detail.structure === null) {
    return (
      <AppLayout>
        {headerRow}
        <Stack spacing={2} sx={{ py: 8, alignItems: 'center' }}>
          <StatusChip status={detail.project.status} />
          <CircularProgress size={32} />
          <Typography variant="body1" color="text.secondary">
            Extracting archive and analyzing project structure… this page will update automatically.
          </Typography>
        </Stack>
      </AppLayout>
    );
  }

  if (detail.project.status === 'FAILED' && detail.project.processingError) {
    return (
      <AppLayout>
        {headerRow}
        <Alert severity="error" sx={{ mt: 2 }}>
          Processing failed: {detail.project.processingError}
        </Alert>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      {headerRow}
      {detail.project.description && (
        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          {detail.project.description}
        </Typography>
      )}

      {analysisError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setAnalysisError(null)}>
          {analysisError}
        </Alert>
      )}

      <Tabs
        value={activeTab}
        onChange={(_, value) => setActiveTab(value)}
        sx={{ mb: 3, borderBottom: '1px solid', borderColor: 'divider' }}
      >
        {TABS.map((label) => (
          <Tab key={label} label={label} />
        ))}
      </Tabs>

      {activeTab === 0 && (
        <OverviewTab
          detail={{ project: detail.project, structure: detail.structure }}
          analysis={analysis}
          onAnalyze={handleAnalyze}
          isAnalyzing={isAnalyzing}
        />
      )}
      {activeTab === 1 && <GeneratedTestsTab tests={analysis?.tests ?? []} />}
      {activeTab === 2 && <SecurityReportTab findings={analysis?.securityFindings ?? []} />}
      {activeTab === 3 && <RiskCoverageTab risk={analysis?.risk} />}
      {activeTab === 4 && (
        <ReportTab projectId={projectId} projectName={detail.project.name} hasAnalysis={analysis !== null} />
      )}
    </AppLayout>
  );
}
