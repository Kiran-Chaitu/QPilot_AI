import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  Chip,
  Grid,
  Skeleton,
  Stack,
  Tab,
  Tabs,
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from '@mui/material';
import {
  Plus,
  FolderArchive,
  ShieldCheck,
  TrendingUp,
  FileCode2,
  AlertTriangle,
  Globe,
  Gauge,
  Sparkles,
  Zap,
  Activity,
  ArrowUpRight,
  FileCode,
  MonitorCheck,
} from 'lucide-react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip as RechartsTooltip,
  PieChart,
  Pie,
  Cell,
} from 'recharts';

import { AppLayout } from '../../components/layout/AppLayout';
import { StatusChip } from '../../components/common/StatusChip';
import { UploadProjectDialog } from './UploadProjectDialog';
import { listProjects } from '../../api/projectApi';
import { getDashboardStats, type DashboardStats } from '../../api/dashboardApi';
import { extractErrorMessage } from '../../api/httpClient';
import type { ProjectResponse } from '../../types/project';

// Tab components
import { WebsiteAuditorTab } from '../project/tabs/WebsiteAuditorTab';
import { LoadTesterTab } from '../project/tabs/LoadTesterTab';
import { SecurityReportTab } from '../project/tabs/SecurityReportTab';
import { GeneratedTestsTab } from '../project/tabs/GeneratedTestsTab';
import { ReportTab } from '../project/tabs/ReportTab';
import { E2eTestTab } from '../project/tabs/E2eTestTab';

const TEST_TYPE_COLORS: Record<string, string> = {
  UNIT: '#10B981',
  API: '#34D399',
  SECURITY: '#F59E0B',
  INTEGRATION: '#A855F7',
  EDGE_CASE: '#6366F1',
};

export function DashboardPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const currentTab = searchParams.get('tab') || 'overview';

  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  async function loadProjects() {
    setIsLoading(true);
    setError(null);
    try {
      const [projectData, statsData] = await Promise.all([
        listProjects(),
        getDashboardStats().catch(() => null),
      ]);
      setProjects(projectData);
      if (statsData) setStats(statsData);
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load your projects.'));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadProjects();
  }, []);

  const handleTabChange = (_e: React.SyntheticEvent, newValue: string) => {
    if (newValue === 'overview') {
      setSearchParams({});
    } else {
      setSearchParams({ tab: newValue });
    }
  };

  const totalProjects = stats?.totalProjects ?? projects.length;
  const analyzedProjects = stats?.analyzedProjects ?? projects.filter((p) => p.status === 'ANALYZED').length;
  const avgCoverage = stats?.avgCoveragePercent ?? 0;
  const avgRisk = stats?.avgRiskScore ?? 0;
  const totalTests = stats?.totalTestsGenerated ?? 0;

  // Build chart-ready test distribution data from real stats
  const testDistData = (stats?.testDistribution ?? []).map((td) => ({
    name: td.type.charAt(0) + td.type.slice(1).toLowerCase().replace('_', ' ') + ' Tests',
    value: td.count,
    color: TEST_TYPE_COLORS[td.type] || '#A1A1AA',
  }));

  // Use real advice from security findings
  const qualityAdvice = (stats?.topAdvice ?? []).slice(0, 3).map((a) => ({
    title: a.category.replace(/_/g, ' '),
    desc: a.description,
  }));

  return (
    <AppLayout disableScroll={false} onRefreshProjects={loadProjects}>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pb: 4 }}>
        {/* Header Action Bar */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexShrink: 0 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Typography variant="h5" sx={{ fontWeight: 800, fontSize: '1.4rem' }}>
              Quality Command Center
            </Typography>
            <Chip
              icon={<Sparkles size={13} color="#10B981" />}
              label="AI Agents Active"
              color="primary"
              size="small"
              variant="outlined"
              sx={{ height: 22, fontSize: '0.7rem', fontWeight: 700 }}
            />
          </Box>

          <Stack direction="row" spacing={1.5}>
            <Button
              variant="contained"
              color="primary"
              size="small"
              startIcon={<Plus size={16} />}
              onClick={() => setDialogOpen(true)}
              sx={{ fontWeight: 800, borderRadius: 2, px: 2.5 }}
            >
              Import Project
            </Button>
          </Stack>
        </Box>

        {/* Tab Navigation Bar */}
        <Box sx={{ borderBottom: 1, borderColor: 'divider', flexShrink: 0 }}>
          <Tabs
            value={currentTab}
            onChange={handleTabChange}
            variant="scrollable"
            scrollButtons="auto"
            sx={{
              minHeight: 36,
              '& .MuiTab-root': { py: 0.8, px: 2, minHeight: 36, fontSize: '0.85rem', fontWeight: 600 },
              '& .Mui-selected': { fontWeight: 800, color: 'primary.main' },
            }}
          >
            <Tab label="Overview & Workspace" value="overview" />
            <Tab label="Synthetic Web Auditor" value="website" icon={<Globe size={14} />} iconPosition="start" />
            <Tab label="Safe Load Tester" value="loadtest" icon={<Gauge size={14} />} iconPosition="start" />
            <Tab label="Security Audit" value="security" icon={<ShieldCheck size={14} />} iconPosition="start" />
            <Tab label="AI Test Generators" value="tests" icon={<FileCode size={14} />} iconPosition="start" />
            <Tab label="E2E Browser Test" value="e2e" icon={<MonitorCheck size={14} />} iconPosition="start" />
            <Tab label="Quality Reports" value="reports" />
          </Tabs>
        </Box>

        {error && (
          <Paper color="error" sx={{ p: 1.5, bgcolor: 'error.main', color: '#fff', borderRadius: 2 }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{error}</Typography>
          </Paper>
        )}

        {/* OVERVIEW TAB - FULL RESPONSIVE DASHBOARD WITH RELIABLE CHARTS */}
        {currentTab === 'overview' && (
          <Stack spacing={2}>
            {/* KPI Stat Cards (Row 1) */}
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Card sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, fontSize: '0.68rem', letterSpacing: '0.04em' }}>
                      TOTAL PROJECTS
                    </Typography>
                    <Typography variant="h4" sx={{ fontWeight: 800, lineHeight: 1.2, my: 0.5 }}>
                      {isLoading ? <Skeleton width={40} /> : totalProjects}
                    </Typography>
                    <Typography variant="caption" color="primary.main" sx={{ fontWeight: 700, fontSize: '0.7rem' }}>
                      {analyzedProjects} Analyzed Repository
                    </Typography>
                  </Box>
                  <Box sx={{ p: 1.2, borderRadius: 2.5, bgcolor: 'rgba(16, 185, 129, 0.12)' }}>
                    <FolderArchive size={24} color="#10B981" />
                  </Box>
                </Card>
              </Grid>

              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Card sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, fontSize: '0.68rem', letterSpacing: '0.04em' }}>
                      AVG CODE COVERAGE
                    </Typography>
                    <Typography variant="h4" sx={{ fontWeight: 800, color: 'success.main', lineHeight: 1.2, my: 0.5 }}>
                      {isLoading ? <Skeleton width={60} /> : `${avgCoverage}%`}
                    </Typography>
                    <Typography variant="caption" color="success.main" sx={{ fontWeight: 700, fontSize: '0.7rem' }}>
                      {avgCoverage > 0 ? 'Across All Projects' : 'No Data Yet'}
                    </Typography>
                  </Box>
                  <Box sx={{ p: 1.2, borderRadius: 2.5, bgcolor: 'rgba(16, 185, 129, 0.12)' }}>
                    <TrendingUp size={24} color="#10B981" />
                  </Box>
                </Card>
              </Grid>

              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Card sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, fontSize: '0.68rem', letterSpacing: '0.04em' }}>
                      SYSTEM RISK INDEX
                    </Typography>
                    <Typography variant="h4" sx={{ fontWeight: 800, color: 'warning.main', lineHeight: 1.2, my: 0.5 }}>
                      {isLoading ? <Skeleton width={60} /> : <>{avgRisk} <Typography component="span" variant="caption" color="text.secondary">/ 100</Typography></>}
                    </Typography>
                    <Typography variant="caption" color="success.main" sx={{ fontWeight: 700, fontSize: '0.7rem' }}>
                      {avgRisk <= 20 ? 'Low Vulnerability Profile' : avgRisk <= 50 ? 'Moderate Risk' : 'High Risk'}
                    </Typography>
                  </Box>
                  <Box sx={{ p: 1.2, borderRadius: 2.5, bgcolor: 'rgba(245, 158, 11, 0.12)' }}>
                    <AlertTriangle size={24} color="#F59E0B" />
                  </Box>
                </Card>
              </Grid>

              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Card sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, fontSize: '0.68rem', letterSpacing: '0.04em' }}>
                      TEST SUITES GENERATED
                    </Typography>
                    <Typography variant="h4" sx={{ fontWeight: 800, color: 'secondary.main', lineHeight: 1.2, my: 0.5 }}>
                      {isLoading ? <Skeleton width={40} /> : totalTests}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.7rem' }}>
                      {totalTests > 0 ? 'JUnit, Playwright, Cypress' : 'No Tests Yet'}
                    </Typography>
                  </Box>
                  <Box sx={{ p: 1.2, borderRadius: 2.5, bgcolor: 'rgba(168, 85, 247, 0.12)' }}>
                    <FileCode2 size={24} color="#A855F7" />
                  </Box>
                </Card>
              </Grid>
            </Grid>

            {/* Analytics Section - Charts with EXPLICIT pixel heights so they NEVER collapse */}
            <Grid container spacing={2}>
              {/* Coverage & Risk Summary */}
              <Grid size={{ xs: 12, lg: 8 }}>
                <Card sx={{ p: 2.5, height: '100%' }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                      Quality & Coverage Overview
                    </Typography>
                    <Chip label="Live Metrics" size="small" color="primary" variant="outlined" sx={{ height: 20, fontSize: '0.68rem', fontWeight: 700 }} />
                  </Box>
                  {analyzedProjects > 0 ? (
                    <Box sx={{ width: '100%', height: 220 }}>
                      <ResponsiveContainer width="100%" height={200}>
                        <AreaChart data={[{ label: 'Coverage', value: avgCoverage }, { label: 'Risk', value: avgRisk }]} margin={{ top: 10, right: 15, left: -20, bottom: 0 }}>
                          <defs>
                            <linearGradient id="coverageGrad" x1="0" y1="0" x2="0" y2="1">
                              <stop offset="5%" stopColor="#10B981" stopOpacity={0.4} />
                              <stop offset="95%" stopColor="#10B981" stopOpacity={0.0} />
                            </linearGradient>
                          </defs>
                          <XAxis dataKey="label" stroke="#A1A1AA" fontSize={11} tickLine={false} />
                          <YAxis stroke="#A1A1AA" fontSize={11} tickLine={false} domain={[0, 100]} />
                          <RechartsTooltip
                            contentStyle={{
                              backgroundColor: '#121215',
                              borderColor: 'rgba(255,255,255,0.1)',
                              borderRadius: 10,
                              color: '#FAFAFA',
                            }}
                          />
                          <Area type="monotone" dataKey="value" stroke="#10B981" strokeWidth={2.5} fillOpacity={1} fill="url(#coverageGrad)" name="Metric Value" />
                        </AreaChart>
                      </ResponsiveContainer>
                    </Box>
                  ) : (
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200, borderRadius: 2, bgcolor: 'rgba(255,255,255,0.02)' }}>
                      <Stack spacing={1} sx={{ alignItems: 'center' }}>
                        <TrendingUp size={32} color="#A1A1AA" />
                        <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                          No analysis data yet
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Run AI analysis on a project to see coverage & risk metrics
                        </Typography>
                      </Stack>
                    </Box>
                  )}
                </Card>
              </Grid>

              {/* Test Type Distribution */}
              <Grid size={{ xs: 12, lg: 4 }}>
                <Card sx={{ p: 2.5, height: '100%', display: 'flex', flexDirection: 'column' }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>
                    Generated Test Mix
                  </Typography>
                  {testDistData.length > 0 ? (
                    <>
                      <Box sx={{ flexGrow: 1, width: '100%', height: 180, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <ResponsiveContainer width="100%" height={160}>
                          <PieChart>
                            <Pie data={testDistData} cx="50%" cy="50%" innerRadius={40} outerRadius={65} paddingAngle={4} dataKey="value">
                              {testDistData.map((entry, index) => (
                                <Cell key={`cell-${index}`} fill={entry.color} />
                              ))}
                            </Pie>
                            <RechartsTooltip
                              contentStyle={{
                                backgroundColor: '#121215',
                                borderColor: 'rgba(255,255,255,0.1)',
                                borderRadius: 10,
                                color: '#FAFAFA',
                              }}
                            />
                          </PieChart>
                        </ResponsiveContainer>
                      </Box>
                      <Box sx={{ display: 'flex', justifyContent: 'space-around', pt: 1 }}>
                        {testDistData.map((item) => (
                          <Box key={item.name} sx={{ textAlign: 'center' }}>
                            <Typography variant="caption" sx={{ color: item.color, fontWeight: 800, fontSize: '0.75rem', display: 'block' }}>
                              {item.value}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.68rem' }}>
                              {item.name.split(' ')[0]}
                            </Typography>
                          </Box>
                        ))}
                      </Box>
                    </>
                  ) : (
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', flexGrow: 1, borderRadius: 2, bgcolor: 'rgba(255,255,255,0.02)' }}>
                      <Stack spacing={1} sx={{ alignItems: 'center' }}>
                        <FileCode2 size={28} color="#A1A1AA" />
                        <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                          No tests generated yet
                        </Typography>
                      </Stack>
                    </Box>
                  )}
                </Card>
              </Grid>
            </Grid>

            {/* Bottom Row - AI Quality Insights & Workspace Repository */}
            <Grid container spacing={2}>
              {/* AI Quality Advice */}
              <Grid size={{ xs: 12, md: 5 }}>
                <Card sx={{ p: 2.5, height: '100%' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                    <Zap size={20} color="#10B981" />
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                      AI Quality Advice
                    </Typography>
                  </Box>
                  <Stack spacing={1.5}>
                    {qualityAdvice.length > 0 ? qualityAdvice.map((rec, i) => (
                      <Paper key={i} sx={{ p: 1.5, borderRadius: 2.5, bgcolor: 'rgba(16, 185, 129, 0.04)', border: '1px solid rgba(16, 185, 129, 0.15)' }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700, fontSize: '0.82rem', color: 'primary.main' }}>
                          {rec.title}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.75rem', display: 'block', mt: 0.5 }}>
                          {rec.desc}
                        </Typography>
                      </Paper>
                    )) : (
                      <Paper sx={{ p: 2.5, borderRadius: 2.5, bgcolor: 'rgba(16, 185, 129, 0.04)', border: '1px solid rgba(16, 185, 129, 0.15)', textAlign: 'center' }}>
                        <Zap size={24} color="#A1A1AA" style={{ marginBottom: 8 }} />
                        <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                          No security findings yet
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Run AI analysis on a project to get quality advice
                        </Typography>
                      </Paper>
                    )}
                  </Stack>
                </Card>
              </Grid>

              {/* Projects Repository Table */}
              <Grid size={{ xs: 12, md: 7 }}>
                <Card sx={{ p: 2.5, height: '100%' }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                      Workspace Repositories ({projects.length})
                    </Typography>
                    <Button size="small" variant="text" onClick={loadProjects} startIcon={<Activity size={12} />} sx={{ fontSize: '0.75rem', fontWeight: 700 }}>
                      Refresh List
                    </Button>
                  </Box>

                  <Box sx={{ overflowX: 'auto' }}>
                    {isLoading ? (
                      <Skeleton variant="rectangular" height={140} sx={{ borderRadius: 2 }} />
                    ) : projects.length === 0 ? (
                      <Box sx={{ textAlign: 'center', py: 4 }}>
                        <FolderArchive size={40} color="#10B981" style={{ marginBottom: 8 }} />
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          No Projects Uploaded Yet
                        </Typography>
                        <Button size="small" variant="contained" color="primary" onClick={() => setDialogOpen(true)} sx={{ mt: 1.5, fontWeight: 700 }}>
                          Import Project
                        </Button>
                      </Box>
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell sx={{ fontSize: '0.75rem' }}>Project Name</TableCell>
                            <TableCell sx={{ fontSize: '0.75rem' }}>Source</TableCell>
                            <TableCell sx={{ fontSize: '0.75rem' }}>Status</TableCell>
                            <TableCell align="right" sx={{ fontSize: '0.75rem' }}>Action</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {projects.map((project) => (
                            <TableRow key={project.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/projects/${project.id}`)}>
                              <TableCell sx={{ fontWeight: 700, fontSize: '0.85rem' }}>
                                {project.name}
                              </TableCell>
                              <TableCell>
                                <Chip label={project.sourceType} size="small" variant="outlined" sx={{ height: 20, fontSize: '0.68rem', fontWeight: 600 }} />
                              </TableCell>
                              <TableCell>
                                <StatusChip status={project.status} />
                              </TableCell>
                              <TableCell align="right">
                                <Typography variant="caption" color="primary.main" sx={{ fontWeight: 800, display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                                  View Audit <ArrowUpRight size={14} />
                                </Typography>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </Box>
                </Card>
              </Grid>
            </Grid>
          </Stack>
        )}

        {/* INTERACTIVE MODULE TABS */}
        {currentTab === 'website' && <WebsiteAuditorTab />}

        {currentTab === 'loadtest' && <LoadTesterTab />}

        {currentTab === 'e2e' && <E2eTestTab />}

        {currentTab === 'security' && <SecurityReportTab findings={[]} />}

        {currentTab === 'tests' && <GeneratedTestsTab tests={[]} />}

        {currentTab === 'reports' && (
          <ReportTab
            projectId={projects[0]?.id || 1}
            projectName={projects[0]?.name || 'Global Quality Workspace'}
            hasAnalysis={true}
          />
        )}
      </Box>

      {/* Upload Modal */}
      <UploadProjectDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onUploaded={(project) => {
          setProjects((prev) => [project, ...prev]);
          navigate(`/projects/${project.id}`);
        }}
      />
    </AppLayout>
  );
}
