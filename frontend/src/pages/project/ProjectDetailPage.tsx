import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Breadcrumbs,
  Button,
  Card,
  Chip,
  CircularProgress,
  IconButton,
  LinearProgress,
  Link,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import {
  ArrowLeft,
  ExternalLink,
  FileCode2,
  FileText,
  Gauge,
  Globe,
  Layers,
  MonitorCheck,
  Play,
  ShieldCheck,
  TrendingUp,
  Zap,
} from 'lucide-react';
import { AppLayout } from '../../components/layout/AppLayout';
import { StatusChip } from '../../components/common/StatusChip';
import { ErrorBoundary } from '../../components/common/ErrorBoundary';
import { ErrorState, LoadingBlock } from '../../components/common/StateViews';
import { getProjectDetail } from '../../api/projectApi';
import { getLatestAnalysis, startAnalysis } from '../../api/analysisApi';
import { extractErrorMessage } from '../../api/httpClient';
import { useToast } from '../../context/ToastContext';
import type { ProjectDetailResponse } from '../../types/project';
import type { AnalysisResultResponse } from '../../types/analysis';
import { OverviewTab } from './tabs/OverviewTab';
import { GeneratedTestsTab } from './tabs/GeneratedTestsTab';
import { SecurityReportTab } from './tabs/SecurityReportTab';
import { RiskCoverageTab } from './tabs/RiskCoverageTab';
import { ReportTab } from './tabs/ReportTab';
import { WebsiteAuditorTab } from './tabs/WebsiteAuditorTab';
import { LoadTesterTab } from './tabs/LoadTesterTab';
import { RateLimitTab } from './tabs/RateLimitTab';
import { E2eTestTab } from './tabs/E2eTestTab';

const TABS = [
  { key: 'overview', label: 'Overview', icon: <Layers size={15} /> },
  { key: 'tests', label: 'Tests', icon: <FileCode2 size={15} /> },
  { key: 'security', label: 'Security', icon: <ShieldCheck size={15} /> },
  { key: 'risk', label: 'Risk & coverage', icon: <TrendingUp size={15} /> },
  { key: 'audit', label: 'Website audit', icon: <Globe size={15} /> },
  { key: 'load', label: 'Load test', icon: <Gauge size={15} /> },
  { key: 'ratelimit', label: 'Rate limits', icon: <Zap size={15} /> },
  { key: 'e2e', label: 'E2E checks', icon: <MonitorCheck size={15} /> },
  { key: 'report', label: 'Report', icon: <FileText size={15} /> },
] as const;

/** Poll cadence while extraction or analysis is in flight. */
const POLL_INTERVAL_MS = 2000;

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const navigate = useNavigate();
  const { showError, showSuccess } = useToast();

  const [detail, setDetail] = useState<ProjectDetailResponse | null>(null);
  const [analysis, setAnalysis] = useState<AnalysisResultResponse | null>(null);
  const [activeTab, setActiveTab] = useState<string>('overview');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isStartingAnalysis, setIsStartingAnalysis] = useState(false);

  const pollTimer = useRef<number | null>(null);

  const isValidId = Number.isFinite(projectId) && projectId > 0;

  const fetchAnalysis = useCallback(async () => {
    try {
      setAnalysis(await getLatestAnalysis(projectId));
    } catch {
      // A 404 here simply means no analysis has been run yet, which is a normal state, not an error.
      setAnalysis(null);
    }
  }, [projectId]);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getProjectDetail(projectId);
      setDetail(data);
      await fetchAnalysis();
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load this project.'));
    } finally {
      setIsLoading(false);
    }
  }, [projectId, fetchAnalysis]);

  useEffect(() => {
    if (isValidId) {
      load();
    } else {
      setIsLoading(false);
      setError(`"${id}" is not a valid project id.`);
    }
  }, [isValidId, load, id]);

  const isExtracting = detail?.project.status === 'EXTRACTING';
  const isAnalyzing = analysis?.run.status === 'RUNNING' || detail?.project.status === 'ANALYZING';

  /**
   * Polls while extraction or analysis is running.
   *
   * <p>Both are genuine background jobs on the server, so the UI reflects their real reported progress
   * rather than animating a guess. Polling stops as soon as neither is in flight.
   */
  useEffect(() => {
    if (!isValidId || (!isExtracting && !isAnalyzing)) {
      return;
    }
    let cancelled = false;

    const tick = async () => {
      try {
        const data = await getProjectDetail(projectId);
        if (cancelled) return;
        setDetail(data);
        const latest = await getLatestAnalysis(projectId).catch(() => null);
        if (cancelled) return;

        if (latest) {
          setAnalysis(latest);
          if (latest.run.status === 'COMPLETED' && isAnalyzing) {
            showSuccess('Analysis complete.');
          } else if (latest.run.status === 'FAILED' && isAnalyzing) {
            showError(latest.run.errorMessage ?? 'Analysis failed.');
          }
        }

        const stillWorking =
          data.project.status === 'EXTRACTING' ||
          data.project.status === 'ANALYZING' ||
          latest?.run.status === 'RUNNING';
        if (stillWorking) {
          pollTimer.current = window.setTimeout(tick, POLL_INTERVAL_MS);
        }
      } catch {
        // Transient polling failures are not surfaced: the next tick usually recovers, and a toast per
        // failed poll would bury the user in noise.
        if (!cancelled) {
          pollTimer.current = window.setTimeout(tick, POLL_INTERVAL_MS * 2);
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
  }, [isValidId, isExtracting, isAnalyzing, projectId, showSuccess, showError]);

  const handleAnalyze = async () => {
    setIsStartingAnalysis(true);
    try {
      const run = await startAnalysis(projectId);
      // Seeded immediately so the progress indicator appears without waiting for the first poll.
      setAnalysis((previous) =>
        previous ? { ...previous, run } : { run, tests: [], securityFindings: [], risk: undefined },
      );
      showSuccess('Analysis started — progress updates here as it runs.');
    } catch (err) {
      showError(extractErrorMessage(err, 'Could not start the analysis.'));
    } finally {
      setIsStartingAnalysis(false);
    }
  };

  if (isLoading) {
    return (
      <AppLayout>
        <Stack spacing={2.5}>
          <LoadingBlock height={120} label="Loading project" />
          <LoadingBlock height={320} />
        </Stack>
      </AppLayout>
    );
  }

  if (error || !detail) {
    return (
      <AppLayout>
        <Stack spacing={2}>
          <ErrorState
            title="Could not load this project"
            message={error ?? 'The project could not be found.'}
            onRetry={isValidId ? load : undefined}
            hint="If the project was deleted, return to the dashboard to see your current workspace."
          />
          <Button startIcon={<ArrowLeft size={16} />} onClick={() => navigate('/dashboard')} sx={{ alignSelf: 'flex-start', fontWeight: 700 }}>
            Back to dashboard
          </Button>
        </Stack>
      </AppLayout>
    );
  }

  const { project, structure } = detail;
  const targetUrl = project.targetUrl ?? project.targetApiUrl;

  const header = (
    <Card sx={{ p: { xs: 2, md: 2.5 }, mb: 2.5 }}>
      <Breadcrumbs sx={{ mb: 1.5, fontSize: 13 }}>
        <Link component={RouterLink} to="/dashboard" color="inherit" underline="hover">
          Dashboard
        </Link>
        <Typography color="text.primary" sx={{ fontSize: 13, fontWeight: 700 }}>
          {project.name}
        </Typography>
      </Breadcrumbs>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { md: 'center' } }}>
        <Box sx={{ minWidth: 0 }}>
          <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', mb: 0.75, flexWrap: 'wrap' }}>
            <IconButton onClick={() => navigate('/dashboard')} size="small" sx={{ border: '1px solid', borderColor: 'divider' }} aria-label="Back to dashboard">
              <ArrowLeft size={16} />
            </IconButton>
            <Typography variant="h5" sx={{ fontWeight: 800, overflowWrap: 'anywhere' }}>
              {project.name}
            </Typography>
            <StatusChip status={project.status} />
            <Chip size="small" variant="outlined" label={project.sourceType.replace('_', ' ')} sx={{ fontWeight: 700 }} />
          </Stack>

          {project.description && (
            <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 680 }}>
              {project.description}
            </Typography>
          )}

          {targetUrl && (
            <Stack direction="row" spacing={0.75} sx={{ mt: 1, alignItems: 'center', minWidth: 0 }}>
              <Globe size={13} />
              <Typography variant="caption" color="text.secondary">
                Target:
              </Typography>
              <Typography
                component="a"
                href={targetUrl}
                target="_blank"
                rel="noopener noreferrer"
                variant="caption"
                className="qp-truncate"
                sx={{ fontFamily: 'var(--font-mono)', fontWeight: 650, color: 'primary.light', textDecoration: 'none' }}
              >
                {targetUrl}
              </Typography>
              <ExternalLink size={11} style={{ opacity: 0.6, flexShrink: 0 }} />
            </Stack>
          )}
        </Box>

        <Button
          variant="contained"
          size="large"
          startIcon={isStartingAnalysis || isAnalyzing ? <CircularProgress size={16} color="inherit" /> : <Play size={17} />}
          onClick={handleAnalyze}
          disabled={isStartingAnalysis || isAnalyzing || isExtracting}
          sx={{ fontWeight: 750, flexShrink: 0 }}
        >
          {isAnalyzing ? 'Analyzing…' : analysis ? 'Re-run analysis' : 'Run analysis'}
        </Button>
      </Stack>

      {isAnalyzing && analysis?.run && (
        <Box sx={{ mt: 2 }}>
          <LinearProgress variant="determinate" value={analysis.run.progressPercent} />
          <Typography variant="caption" color="text.secondary" sx={{ mt: 0.75, display: 'block' }}>
            {analysis.run.progressPercent}% — {analysis.run.currentStage}
          </Typography>
        </Box>
      )}
    </Card>
  );

  if (isExtracting) {
    return (
      <AppLayout>
        {header}
        <Card sx={{ p: 6, textAlign: 'center' }}>
          <Stack spacing={2} sx={{ alignItems: 'center' }}>
            <CircularProgress size={34} />
            <Typography variant="h6" sx={{ fontWeight: 750 }}>
              Extracting and indexing the archive
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 440 }}>
              QPilot is unpacking the archive, skipping build artifacts, and scanning for routes and dependencies. This
              view updates automatically.
            </Typography>
          </Stack>
        </Card>
      </AppLayout>
    );
  }

  if (project.status === 'FAILED' && project.processingError) {
    return (
      <AppLayout>
        {header}
        <ErrorState title="Processing failed" message={project.processingError} onRetry={load} retryLabel="Reload" />
      </AppLayout>
    );
  }

  const renderTab = () => {
    switch (activeTab) {
      case 'overview':
        return (
          <OverviewTab
            project={project}
            structure={structure}
            analysis={analysis}
            onAnalyze={handleAnalyze}
            isAnalyzing={Boolean(isAnalyzing || isStartingAnalysis)}
          />
        );
      case 'tests':
        return <GeneratedTestsTab tests={analysis?.tests ?? []} projectId={projectId} onTestsUpdated={fetchAnalysis} />;
      case 'security':
        return <SecurityReportTab findings={analysis?.securityFindings ?? []} />;
      case 'risk':
        return <RiskCoverageTab risk={analysis?.risk} />;
      case 'audit':
        return <WebsiteAuditorTab defaultUrl={project.targetUrl} />;
      case 'load':
        return <LoadTesterTab defaultApiUrl={project.targetApiUrl ?? project.targetUrl} projectId={projectId} />;
      case 'ratelimit':
        return <RateLimitTab defaultUrl={project.targetApiUrl ?? project.targetUrl} />;
      case 'e2e':
        return <E2eTestTab defaultUrl={project.targetUrl ?? project.targetApiUrl} />;
      case 'report':
        return <ReportTab projectId={projectId} projectName={project.name} hasAnalysis={analysis !== null} />;
      default:
        return null;
    }
  };

  return (
    <AppLayout>
      {header}

      <Box sx={{ borderBottom: '1px solid', borderColor: 'divider', mb: 2.5 }}>
        <Tabs value={activeTab} onChange={(_, value) => setActiveTab(value)} variant="scrollable" scrollButtons="auto">
          {TABS.map((tab) => (
            <Tab key={tab.key} value={tab.key} icon={tab.icon} iconPosition="start" label={tab.label} />
          ))}
        </Tabs>
      </Box>

      {/*
        Per-tab boundary: a crash inside one feature leaves the header, tabs and navigation usable, so
        the user can move elsewhere instead of facing a blank page.
      */}
      <ErrorBoundary boundaryName={`project tab "${activeTab}"`} key={activeTab}>
        <Box className="qp-enter">{renderTab()}</Box>
      </ErrorBoundary>
    </AppLayout>
  );
}
