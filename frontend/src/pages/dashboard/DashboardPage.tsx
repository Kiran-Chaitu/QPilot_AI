import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  Grid,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Activity,
  AlertTriangle,
  ArrowUpRight,
  FileCode2,
  FolderArchive,
  Gauge,
  Plus,
  RefreshCw,
  ShieldAlert,
  Target,
} from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { AppLayout } from '../../components/layout/AppLayout';
import { StatusChip } from '../../components/common/StatusChip';
import { EmptyState, ErrorState, LoadingCards } from '../../components/common/StateViews';
import { OriginChip } from '../../components/common/Provenance';
import { NotAvailable } from '../../components/common/StateViews';
import { UploadProjectDialog } from './UploadProjectDialog';
import { listProjects } from '../../api/projectApi';
import { getDashboardStats, type DashboardStats } from '../../api/dashboardApi';
import { extractErrorMessage } from '../../api/httpClient';
import { brand, chartSeries, riskColor, riskLabel, severityColors, status as statusColors } from '../../theme/palette';
import type { ProjectResponse } from '../../types/project';

function KpiCard({
  label,
  value,
  sub,
  icon,
  accent,
  tooltip,
}: {
  label: string;
  value: React.ReactNode;
  sub?: React.ReactNode;
  icon: React.ReactNode;
  accent: string;
  tooltip?: string;
}) {
  const card = (
    <Card className="qp-lift" sx={{ p: 2.25, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1.5, height: '100%' }}>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="overline" color="text.secondary" sx={{ display: 'block', lineHeight: 1.4 }}>
          {label}
        </Typography>
        <Typography variant="h4" sx={{ fontWeight: 800, my: 0.25, lineHeight: 1.15 }}>
          {value}
        </Typography>
        {sub && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.45 }}>
            {sub}
          </Typography>
        )}
      </Box>
      <Box
        sx={{
          p: 1.15,
          borderRadius: 2.5,
          bgcolor: `${accent}1A`,
          border: `1px solid ${accent}33`,
          display: 'grid',
          placeItems: 'center',
          flexShrink: 0,
        }}
      >
        {icon}
      </Box>
    </Card>
  );
  return tooltip ? <Tooltip title={tooltip}>{card}</Tooltip> : card;
}

export function DashboardPage() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  /**
   * Loads the workspace.
   *
   * <p>Has no dependency on any selection state, so it is created once. The previous version depended on
   * the selected project id, which meant the callback was recreated on every selection change and the
   * effect that used it refetched the entire project list each time.
   */
  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    setStatsError(null);
    const [projectResult, statsResult] = await Promise.allSettled([listProjects(), getDashboardStats()]);

    if (projectResult.status === 'fulfilled') {
      setProjects(projectResult.value);
    } else {
      setError(extractErrorMessage(projectResult.reason, 'Could not load your projects.'));
    }
    // Stats and projects fail independently: losing the metrics panel should not hide the project list.
    if (statsResult.status === 'fulfilled') {
      setStats(statsResult.value);
    } else {
      setStatsError(extractErrorMessage(statsResult.reason, 'Could not load workspace metrics.'));
    }
    setIsLoading(false);
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const testMix = useMemo(
    () =>
      (stats?.testDistribution ?? []).map((entry, index) => ({
        name: entry.type.charAt(0) + entry.type.slice(1).toLowerCase().replace(/_/g, ' '),
        value: entry.count,
        color: chartSeries[index % chartSeries.length],
      })),
    [stats],
  );

  const riskTrend = useMemo(
    () =>
      (stats?.riskHistory ?? []).map((point) => ({
        label: new Date(point.recordedAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
        risk: point.riskScore,
        tested: point.testedSurfacePercent,
        project: point.projectName,
      })),
    [stats],
  );

  const hasAnyAnalysis = (stats?.analyzedProjects ?? 0) > 0;

  return (
    <AppLayout onRefreshProjects={load}>
      <Stack spacing={2.5} sx={{ pb: 4 }}>
        {/* ── Header ─────────────────────────────────────────────────── */}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
          <Box>
            <Typography variant="h5" sx={{ fontWeight: 800 }}>
              Workspace
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Every number below is an aggregate over stored results from real runs.
            </Typography>
          </Box>
          <Stack direction="row" spacing={1.25}>
            <Button variant="outlined" startIcon={<RefreshCw size={15} />} onClick={load} sx={{ fontWeight: 700 }}>
              Refresh
            </Button>
            <Button variant="contained" startIcon={<Plus size={16} />} onClick={() => setDialogOpen(true)} sx={{ fontWeight: 750 }}>
              Add project
            </Button>
          </Stack>
        </Stack>

        {error && <ErrorState title="Could not load projects" message={error} onRetry={load} />}
        {statsError && <ErrorState compact message={statsError} onRetry={load} />}

        {/* ── KPIs ───────────────────────────────────────────────────── */}
        {isLoading ? (
          <LoadingCards count={4} />
        ) : (
          <Grid container spacing={2} className="qp-stagger">
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                label="Projects"
                value={stats?.totalProjects ?? projects.length}
                sub={`${stats?.analyzedProjects ?? 0} analyzed`}
                icon={<FolderArchive size={22} color={brand.primary} />}
                accent={brand.primary}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                label="Tests"
                value={stats?.totalTestsGenerated ?? 0}
                sub={
                  (stats?.testsExecuted ?? 0) === 0 ? (
                    <span>
                      generated · <strong>0 executed</strong>
                    </span>
                  ) : (
                    <span>
                      {stats?.testsExecuted} executed ·{' '}
                      <Box component="span" sx={{ color: statusColors.successText, fontWeight: 750 }}>
                        {stats?.testsPassed} passed
                      </Box>{' '}
                      ·{' '}
                      <Box component="span" sx={{ color: statusColors.errorText, fontWeight: 750 }}>
                        {stats?.testsFailed} failed
                      </Box>
                    </span>
                  )
                }
                icon={<FileCode2 size={22} color={brand.secondary} />}
                accent={brand.secondary}
                tooltip="Generated counts tests that exist. Executed/passed/failed come only from tests QPilot actually ran against a live target — a generated test that was never run is never counted as passing."
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                label="Avg risk score"
                value={
                  stats?.avgRiskScore === null || stats?.avgRiskScore === undefined ? (
                    <NotAvailable reason="No project has been analyzed yet, so there is no risk score to average. This is deliberately not shown as 0." />
                  ) : (
                    <Box component="span" sx={{ color: riskColor(stats.avgRiskScore) }}>
                      {stats.avgRiskScore}
                      <Typography component="span" variant="caption" color="text.secondary">
                        {' '}
                        / 100
                      </Typography>
                    </Box>
                  )
                }
                sub={
                  stats?.avgRiskScore !== null && stats?.avgRiskScore !== undefined
                    ? riskLabel(stats.avgRiskScore)
                    : 'run an analysis to populate'
                }
                icon={<AlertTriangle size={22} color={statusColors.warning} />}
                accent={statusColors.warning}
                tooltip="Averaged across the latest assessment per project, so re-running an analysis does not skew the figure."
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                label="Security findings"
                value={stats?.totalSecurityFindings ?? 0}
                sub={
                  (stats?.totalSecurityFindings ?? 0) > 0 ? (
                    <span>
                      <Box component="span" sx={{ color: severityColors.CRITICAL, fontWeight: 750 }}>
                        {stats?.criticalFindings} critical
                      </Box>{' '}
                      · {stats?.highFindings} high · {stats?.mediumFindings} medium
                    </span>
                  ) : (
                    'none recorded'
                  )
                }
                icon={<ShieldAlert size={22} color={severityColors.CRITICAL} />}
                accent={severityColors.CRITICAL}
              />
            </Grid>
          </Grid>
        )}

        {/* ── Charts from real history ───────────────────────────────── */}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 8 }}>
            <Card sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
                <Activity size={18} color={brand.primary} />
                <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                  Risk &amp; tested surface over time
                </Typography>
                {riskTrend.length > 0 && <Chip size="small" variant="outlined" label={`${riskTrend.length} assessments`} />}
              </Stack>

              {riskTrend.length === 0 ? (
                <EmptyState
                  dense
                  icon={<Activity size={22} />}
                  title="No history yet"
                  description="Each analysis you run stores an assessment. Once there are two or more, this chart plots how your workspace actually moved — it is not drawn until there is real history to draw."
                  action={
                    projects.length === 0 ? (
                      <Button variant="contained" startIcon={<Plus size={15} />} onClick={() => setDialogOpen(true)} sx={{ fontWeight: 750 }}>
                        Add your first project
                      </Button>
                    ) : undefined
                  }
                />
              ) : (
                <Box sx={{ width: '100%', height: 260 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={riskTrend} margin={{ top: 6, right: 10, left: -18, bottom: 0 }}>
                      <defs>
                        <linearGradient id="dashRisk" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor={statusColors.warning} stopOpacity={0.4} />
                          <stop offset="95%" stopColor={statusColors.warning} stopOpacity={0} />
                        </linearGradient>
                        <linearGradient id="dashTested" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor={statusColors.success} stopOpacity={0.35} />
                          <stop offset="95%" stopColor={statusColors.success} stopOpacity={0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--qp-border)" vertical={false} />
                      <XAxis dataKey="label" stroke="var(--qp-text-muted)" fontSize={11} tickLine={false} />
                      <YAxis stroke="var(--qp-text-muted)" fontSize={11} tickLine={false} domain={[0, 100]} />
                      <RechartsTooltip
                        contentStyle={{ background: 'var(--qp-elevated)', border: '1px solid var(--qp-border)', borderRadius: 10, fontSize: 12 }}
                        labelFormatter={(_, payload) => payload?.[0]?.payload?.project ?? ''}
                      />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      <Area type="monotone" dataKey="risk" name="Risk score" stroke={statusColors.warning} strokeWidth={2} fill="url(#dashRisk)" />
                      <Area type="monotone" dataKey="tested" name="Tested surface %" stroke={statusColors.success} strokeWidth={2} fill="url(#dashTested)" />
                    </AreaChart>
                  </ResponsiveContainer>
                </Box>
              )}
            </Card>
          </Grid>

          <Grid size={{ xs: 12, lg: 4 }}>
            <Card sx={{ p: { xs: 2, md: 2.5 }, height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 750, mb: 1 }}>
                Generated test mix
              </Typography>
              {testMix.length === 0 ? (
                <Box sx={{ flexGrow: 1, display: 'grid', placeItems: 'center' }}>
                  <EmptyState dense icon={<FileCode2 size={22} />} title="No tests generated yet" />
                </Box>
              ) : (
                <>
                  <Box sx={{ width: '100%', height: 190 }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie data={testMix} cx="50%" cy="50%" innerRadius={48} outerRadius={72} paddingAngle={3} dataKey="value" stroke="none">
                          {testMix.map((entry) => (
                            <Cell key={entry.name} fill={entry.color} />
                          ))}
                        </Pie>
                        <RechartsTooltip
                          contentStyle={{ background: 'var(--qp-elevated)', border: '1px solid var(--qp-border)', borderRadius: 10, fontSize: 12 }}
                        />
                      </PieChart>
                    </ResponsiveContainer>
                  </Box>
                  <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1, justifyContent: 'center' }}>
                    {testMix.map((entry) => (
                      <Stack key={entry.name} direction="row" spacing={0.6} sx={{ alignItems: 'center' }}>
                        <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: entry.color }} />
                        <Typography variant="caption" color="text.secondary">
                          {entry.name} · <strong>{entry.value}</strong>
                        </Typography>
                      </Stack>
                    ))}
                  </Stack>
                </>
              )}
            </Card>
          </Grid>
        </Grid>

        {/* ── Load-test aggregate (only when runs exist) ─────────────── */}
        {stats?.loadTestSummary && (
          <Card sx={{ p: { xs: 2, md: 2.5 } }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
              <Gauge size={18} color={brand.secondary} />
              <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                Load testing
              </Typography>
              <Chip size="small" variant="outlined" label={`${stats.loadTestSummary.completedRuns} run(s)`} />
            </Stack>
            <Grid container spacing={2}>
              <Grid size={{ xs: 6, md: 3 }}>
                <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
                  Total requests sent
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  {stats.loadTestSummary.totalRequests.toLocaleString()}
                </Typography>
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
                  Avg response time
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  {stats.loadTestSummary.avgResponseTimeMs} ms
                </Typography>
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
                  Avg error rate
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  {stats.loadTestSummary.avgErrorRatePercent}%
                </Typography>
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
                  Last run
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700, mt: 0.5 }}>
                  {stats.loadTestSummary.lastRunAt ? new Date(stats.loadTestSummary.lastRunAt).toLocaleString() : '—'}
                </Typography>
              </Grid>
            </Grid>
          </Card>
        )}

        {/* ── Findings + projects ────────────────────────────────────── */}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <Card sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.75 }}>
                <ShieldAlert size={18} color={severityColors.HIGH} />
                <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                  Highest-severity findings
                </Typography>
              </Stack>
              {(stats?.topAdvice ?? []).length === 0 ? (
                <EmptyState
                  dense
                  icon={<Target size={22} />}
                  title={hasAnyAnalysis ? 'No findings recorded' : 'Nothing analyzed yet'}
                  description={
                    hasAnyAnalysis
                      ? "None of QPilot's static rules matched your source. That covers the shipped rule set — it is not a full security audit."
                      : 'Run an analysis on a project to populate this panel.'
                  }
                />
              ) : (
                <Stack spacing={1.25}>
                  {stats?.topAdvice.map((advice, index) => {
                    const color = severityColors[advice.severity] ?? statusColors.info;
                    return (
                      <Paper
                        key={index}
                        sx={{ p: 1.6, borderRadius: 2.5, border: '1px solid', borderColor: 'divider', borderLeft: `3px solid ${color}` }}
                      >
                        <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', mb: 0.5, flexWrap: 'wrap' }}>
                          <Chip size="small" label={advice.severity} sx={{ fontWeight: 800, color, bgcolor: `${color}1F` }} />
                          <Typography variant="caption" sx={{ fontWeight: 750 }}>
                            {advice.category.replace(/_/g, ' ')}
                          </Typography>
                          <OriginChip origin={advice.origin === 'AI_SUGGESTION' ? 'AI_SUGGESTION' : 'STATIC_ANALYSIS'} />
                        </Stack>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                          {advice.description}
                        </Typography>
                        {advice.location && (
                          <Typography variant="caption" sx={{ display: 'block', mt: 0.5, fontFamily: 'var(--font-mono)', fontSize: '0.68rem', opacity: 0.75, overflowWrap: 'anywhere' }}>
                            {advice.location}
                          </Typography>
                        )}
                      </Paper>
                    );
                  })}
                </Stack>
              )}
            </Card>
          </Grid>

          <Grid size={{ xs: 12, lg: 7 }}>
            <Card sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1.75 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                  Projects ({projects.length})
                </Typography>
              </Stack>

              {isLoading ? (
                <LoadingCards count={2} height={72} />
              ) : projects.length === 0 ? (
                <EmptyState
                  icon={<FolderArchive size={24} />}
                  title="No projects yet"
                  description="Upload a source archive to get code-level analysis, or point QPilot at a live URL to have it discover the API from an OpenAPI document."
                  action={
                    <Button variant="contained" startIcon={<Plus size={15} />} onClick={() => setDialogOpen(true)} sx={{ fontWeight: 750 }}>
                      Add project
                    </Button>
                  }
                />
              ) : (
                <Box className="qp-scroll-x">
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Project</TableCell>
                        <TableCell>Source</TableCell>
                        <TableCell>Language</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Open</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {projects.map((project) => (
                        <TableRow key={project.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/projects/${project.id}`)}>
                          <TableCell sx={{ maxWidth: 240 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }} className="qp-truncate">
                              {project.name}
                            </Typography>
                            {project.fileCount !== undefined && project.fileCount > 0 && (
                              <Typography variant="caption" color="text.secondary">
                                {project.fileCount} files
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell>
                            <Chip size="small" variant="outlined" label={project.sourceType.replace('_', ' ')} sx={{ fontWeight: 650 }} />
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" color="text.secondary">
                              {project.primaryLanguage ?? '—'}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <StatusChip status={project.status} />
                          </TableCell>
                          <TableCell align="right">
                            <ArrowUpRight size={15} style={{ opacity: 0.6 }} />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}
            </Card>
          </Grid>
        </Grid>

        {(stats?.testsNotExecutable ?? 0) > 0 && (
          <Alert severity="info" variant="outlined" sx={{ borderRadius: 3 }}>
            <Typography variant="caption" color="text.secondary">
              {stats?.testsNotExecutable} generated test(s) cannot be executed by QPilot — unit tests need your
              project&apos;s own compiler and test runner. They are reported as generated rather than passing, and the
              code is complete and downloadable.
            </Typography>
          </Alert>
        )}
      </Stack>

      <UploadProjectDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onUploaded={(project) => {
          setProjects((previous) => [project, ...previous]);
          navigate(`/projects/${project.id}`);
        }}
      />
    </AppLayout>
  );
}
