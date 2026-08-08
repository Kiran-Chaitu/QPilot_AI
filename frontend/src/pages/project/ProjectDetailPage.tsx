import { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate, Link as RouterLink } from 'react-router-dom';
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Card,
  Chip,
  CircularProgress,
  IconButton,
  Link,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import {
  ArrowLeft,
  Play,
  Globe,
  Gauge,
  ShieldCheck,
  FileCode2,
  FileText,
  Layers,
  TrendingUp,
  ExternalLink,
  MonitorCheck,
} from 'lucide-react';
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
import { WebsiteAuditorTab } from './tabs/WebsiteAuditorTab';
import { LoadTesterTab } from './tabs/LoadTesterTab';
import { E2eTestTab } from './tabs/E2eTestTab';

const TABS = [
  { label: 'Overview & Stack', icon: <Layers size={16} /> },
  { label: 'Generated Tests', icon: <FileCode2 size={16} /> },
  { label: 'Synthetic Web Auditor', icon: <Globe size={16} /> },
  { label: 'Safe Load Tester', icon: <Gauge size={16} /> },
  { label: 'E2E Browser Test', icon: <MonitorCheck size={16} /> },
  { label: 'Security Audit', icon: <ShieldCheck size={16} /> },
  { label: 'Risk & Coverage', icon: <TrendingUp size={16} /> },
  { label: 'Executive Report', icon: <FileText size={16} /> },
] as const;

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

  const loadProject = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getProjectDetail(projectId);
      setDetail(data);
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

  useEffect(() => {
    if (detail?.project.status !== 'EXTRACTING') {
      return;
    }
    const interval = setInterval(async () => {
      try {
        const data = await getProjectDetail(projectId);
        setDetail(data);
        if (data.project.status !== 'EXTRACTING') {
          clearInterval(interval);
          // Fetch analysis data once extraction completes
          try {
            const latest = await getLatestAnalysis(projectId);
            setAnalysis(latest);
          } catch {
            // No analysis yet — that's fine
          }
        }
      } catch {
        // ignore polling errors
      }
    }, EXTRACTING_POLL_INTERVAL_MS);
    return () => clearInterval(interval);
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
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', py: 12 }}>
          <CircularProgress size={40} />
        </Box>
      </AppLayout>
    );
  }

  if (error || !detail) {
    return (
      <AppLayout>
        <Alert severity="error" sx={{ borderRadius: 2 }}>
          {error ?? 'Project not found.'}
        </Alert>
      </AppLayout>
    );
  }

  const headerCard = (
    <Card sx={{ p: 3, mb: 3, border: '1px solid rgba(255, 255, 255, 0.09)' }}>
      {/* Breadcrumbs */}
      <Breadcrumbs sx={{ mb: 2, fontSize: 13 }}>
        <Link component={RouterLink} to="/dashboard" color="inherit" underline="hover">
          Dashboard
        </Link>
        <Typography color="text.primary" sx={{ fontSize: 13, fontWeight: 700 }}>
          {detail.project.name}
        </Typography>
      </Breadcrumbs>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { md: 'center' } }}>
        <Box>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap' }}>
            <IconButton onClick={() => navigate('/dashboard')} size="small" sx={{ border: '1px solid', borderColor: 'divider' }}>
              <ArrowLeft size={18} />
            </IconButton>
            <Typography variant="h4" sx={{ fontWeight: 800 }}>
              {detail.project.name}
            </Typography>
            <StatusChip status={detail.project.status} />
            <Chip label={detail.project.sourceType} color="primary" variant="outlined" size="small" sx={{ fontWeight: 700 }} />
          </Stack>

          <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 700 }}>
            {detail.project.description || 'Enterprise repository for continuous automated multi-framework AI quality testing.'}
          </Typography>

          {(detail.project.targetUrl || detail.project.targetApiUrl) && (
            <Stack direction="row" spacing={1} sx={{ mt: 1.5, alignItems: 'center' }}>
              <Globe size={14} color="#6366F1" />
              <Typography variant="caption" color="text.secondary">
                Target:
              </Typography>
              <Typography variant="caption" sx={{ fontFamily: 'JetBrains Mono', fontWeight: 600, color: 'primary.main' }}>
                {detail.project.targetUrl || detail.project.targetApiUrl}
              </Typography>
              <ExternalLink size={12} style={{ opacity: 0.6 }} />
            </Stack>
          )}
        </Box>

        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Button
            variant="contained"
            color="primary"
            size="large"
            startIcon={isAnalyzing ? <CircularProgress size={16} color="inherit" /> : <Play size={18} />}
            onClick={handleAnalyze}
            disabled={isAnalyzing}
            sx={{ fontWeight: 700, borderRadius: 2, px: 3 }}
          >
            {isAnalyzing ? 'Running AI Agents…' : analysis ? 'Re-Run AI Multi-Agent Audit' : 'Run Full Quality Audit'}
          </Button>
        </Stack>
      </Stack>
    </Card>
  );

  if (detail.project.status === 'EXTRACTING' || detail.structure === null) {
    return (
      <AppLayout>
        {headerCard}
        <Stack spacing={2} sx={{ py: 8, alignItems: 'center', textAlign: 'center' }}>
          <StatusChip status={detail.project.status} />
          <CircularProgress size={36} />
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            Extracting Archive & Analyzing Structure…
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 460 }}>
            QPilot AI agents are indexing files, parsing annotations & building the project RAG graph. This view updates automatically.
          </Typography>
        </Stack>
      </AppLayout>
    );
  }

  if (detail.project.status === 'FAILED' && detail.project.processingError) {
    return (
      <AppLayout>
        {headerCard}
        <Alert severity="error" sx={{ mt: 2, borderRadius: 2 }}>
          Processing failed: {detail.project.processingError}
        </Alert>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      {headerCard}

      {analysisError && (
        <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }} onClose={() => setAnalysisError(null)}>
          {analysisError}
        </Alert>
      )}

      {/* Tabs Menu */}
      <Box sx={{ borderBottom: '1px solid', borderColor: 'divider', mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, value) => setActiveTab(value)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            '& .MuiTab-root': {
              fontWeight: 600,
              fontSize: '0.9rem',
              py: 1.5,
              px: 2.5,
              transition: 'all 0.2s ease',
            },
            '& .Mui-selected': {
              fontWeight: 800,
              color: 'primary.main',
            },
          }}
        >
          {TABS.map((t) => (
            <Tab
              key={t.label}
              icon={t.icon}
              iconPosition="start"
              label={t.label}
            />
          ))}
        </Tabs>
      </Box>

      {/* Tab Panels */}
      {activeTab === 0 && (
        <OverviewTab
          detail={{ project: detail.project, structure: detail.structure }}
          analysis={analysis}
          onAnalyze={handleAnalyze}
          isAnalyzing={isAnalyzing}
        />
      )}

      {activeTab === 1 && <GeneratedTestsTab tests={analysis?.tests ?? []} />}

      {activeTab === 2 && <WebsiteAuditorTab defaultUrl={detail.project.targetUrl} />}

      {activeTab === 3 && <LoadTesterTab defaultApiUrl={detail.project.targetApiUrl} />}

      {activeTab === 4 && <E2eTestTab defaultUrl={detail.project.targetUrl} />}

      {activeTab === 5 && <SecurityReportTab findings={analysis?.securityFindings ?? []} />}

      {activeTab === 6 && <RiskCoverageTab risk={analysis?.risk} />}

      {activeTab === 7 && (
        <ReportTab projectId={projectId} projectName={detail.project.name} hasAnalysis={analysis !== null} />
      )}
    </AppLayout>
  );
}
