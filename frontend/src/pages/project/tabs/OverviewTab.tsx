import {
  Box,
  Button,
  Card,
  Chip,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import {
  Play,
  CheckCircle2,
  AlertCircle,
  FileCode2,
  Layers,
  Code2,
  Cpu,
  Sparkles,
  Zap,
} from 'lucide-react';
import { StatusChip } from '../../../components/common/StatusChip';
import type { ProjectResponse, ProjectStructureSummary } from '../../../types/project';
import type { AnalysisResultResponse } from '../../../types/analysis';

interface OverviewTabProps {
  detail: { project: ProjectResponse; structure: ProjectStructureSummary };
  analysis: AnalysisResultResponse | null;
  onAnalyze: () => void;
  isAnalyzing: boolean;
}

export function OverviewTab({ detail, analysis, onAnalyze, isAnalyzing }: OverviewTabProps) {
  const { project, structure } = detail;

  return (
    <Grid container spacing={3}>
      {/* Left Column: Repository Structure & API Map */}
      <Grid size={{ xs: 12, md: 7 }}>
        <Card sx={{ p: 2.5, mb: 3 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Layers size={20} color="#10B981" />
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                Repository & Tech Stack Profile
              </Typography>
            </Box>
            <StatusChip status={project.status} />
          </Box>

          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid size={4}>
              <Paper sx={{ p: 1.5, borderRadius: 2, bgcolor: 'action.hover', border: '1px solid', borderColor: 'divider' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  PRIMARY LANGUAGE
                </Typography>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, color: 'primary.main', mt: 0.5 }}>
                  {structure.primaryLanguage ?? 'Auto-Detected'}
                </Typography>
              </Paper>
            </Grid>
            <Grid size={4}>
              <Paper sx={{ p: 1.5, borderRadius: 2, bgcolor: 'action.hover', border: '1px solid', borderColor: 'divider' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  TOTAL FILES INDEXED
                </Typography>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mt: 0.5 }}>
                  {structure.totalFiles} Files
                </Typography>
              </Paper>
            </Grid>
            <Grid size={4}>
              <Paper sx={{ p: 1.5, borderRadius: 2, bgcolor: 'action.hover', border: '1px solid', borderColor: 'divider' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  API ENDPOINTS
                </Typography>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, color: 'secondary.main', mt: 0.5 }}>
                  {structure.endpoints.length} Endpoints
                </Typography>
              </Paper>
            </Grid>
          </Grid>

          {Object.keys(structure.languageBreakdown).length > 0 && (
            <Box sx={{ mb: 3 }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, display: 'block', mb: 1 }}>
                LANGUAGE DISTRIBUTION
              </Typography>
              <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                {Object.entries(structure.languageBreakdown).map(([lang, count]) => (
                  <Chip
                    key={lang}
                    size="small"
                    label={`${lang}: ${count}`}
                    sx={{ fontWeight: 700, borderRadius: 1.5 }}
                  />
                ))}
              </Stack>
            </Box>
          )}

          {structure.dependencies.length > 0 && (
            <Box sx={{ mb: 3 }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, display: 'block', mb: 1 }}>
                DETECTED DEPENDENCIES & LIBRARIES
              </Typography>
              <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                {structure.dependencies.slice(0, 16).map((dep) => (
                  <Chip
                    key={dep}
                    size="small"
                    variant="outlined"
                    label={dep}
                    sx={{ fontWeight: 600, borderRadius: 1.5, fontSize: '0.75rem' }}
                  />
                ))}
              </Stack>
            </Box>
          )}

          {structure.endpoints.length > 0 && (
            <Box sx={{ mt: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                <Code2 size={18} color="#3B82F6" />
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                  Discovered API Route Handlers
                </Typography>
              </Box>
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Method</TableCell>
                      <TableCell>Route Path</TableCell>
                      <TableCell>Controller / Source File</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {structure.endpoints.map((ep, idx) => (
                      <TableRow key={idx} hover>
                        <TableCell>
                          <Chip
                            size="small"
                            label={ep.httpMethod}
                            color={
                              ep.httpMethod === 'GET'
                                ? 'success'
                                : ep.httpMethod === 'POST'
                                ? 'primary'
                                : ep.httpMethod === 'DELETE'
                                ? 'error'
                                : 'warning'
                            }
                            sx={{ fontWeight: 800, fontSize: '0.68rem', height: 20 }}
                          />
                        </TableCell>
                        <TableCell>
                          <Typography variant="caption" sx={{ fontFamily: 'JetBrains Mono', fontWeight: 600, color: 'primary.main' }}>
                            {ep.path}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'JetBrains Mono' }}>
                            {ep.sourceFile}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            </Box>
          )}
        </Card>
      </Grid>

      {/* Right Column: AI Code Understanding & Agent Insights */}
      <Grid size={{ xs: 12, md: 5 }}>
        <Card sx={{ p: 2.5, mb: 3 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Cpu size={20} color="#8B5CF6" />
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                AI Code Understanding Agent
              </Typography>
            </Box>
            <Button
              variant="contained"
              size="small"
              startIcon={<Play size={14} />}
              onClick={onAnalyze}
              disabled={isAnalyzing}
              sx={{ fontWeight: 700 }}
            >
              {isAnalyzing ? 'Analyzing…' : analysis ? 'Re-Run AI' : 'Run Audit'}
            </Button>
          </Box>

          {!analysis ? (
            <Box sx={{ py: 5, textAlign: 'center' }}>
              <Sparkles size={42} color="#10B981" style={{ marginBottom: 12, opacity: 0.8 }} />
              <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 0.5 }}>
                Ready to Reason Over Repository
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 320, mx: 'auto', mb: 2 }}>
                Click &quot;Run Audit&quot; to invoke Gemini 3.6 multi-agent reasoning for code summarization, unit test generation, security scanning & risk scoring.
              </Typography>
              <Button variant="contained" color="primary" onClick={onAnalyze} disabled={isAnalyzing} startIcon={<Zap size={16} />}>
                Start Autonomous Audit
              </Button>
            </Box>
          ) : (
            <Box>
              <Paper sx={{ p: 2, borderRadius: 2.5, bgcolor: 'rgba(16, 185, 129, 0.05)', border: '1px solid rgba(16, 185, 129, 0.2)', mb: 2 }}>
                <Typography variant="caption" color="primary.main" sx={{ fontWeight: 800, letterSpacing: '0.04em', display: 'block', mb: 0.5 }}>
                  ARCHITECTURAL SUMMARY
                </Typography>
                <Typography variant="body2" sx={{ lineHeight: 1.6, fontWeight: 500 }}>
                  {analysis.run.codeSummary}
                </Typography>
              </Paper>

              {analysis.run.keyResponsibilities.length > 0 && (
                <Box sx={{ mb: 2 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, display: 'block', mb: 1 }}>
                    KEY SYSTEM RESPONSIBILITIES
                  </Typography>
                  <List dense disablePadding>
                    {analysis.run.keyResponsibilities.map((item, idx) => (
                      <ListItem key={idx} disableGutters sx={{ py: 0.5 }}>
                        <ListItemIcon sx={{ minWidth: 26 }}>
                          <CheckCircle2 size={16} color="#10B981" />
                        </ListItemIcon>
                        <ListItemText primary={<Typography variant="body2" sx={{ fontWeight: 600 }}>{item}</Typography>} />
                      </ListItem>
                    ))}
                  </List>
                </Box>
              )}

              {analysis.run.notableObservations.length > 0 && (
                <Box sx={{ mb: 2 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, display: 'block', mb: 1 }}>
                    NOTABLE OBSERVATIONS & RISKS
                  </Typography>
                  <List dense disablePadding>
                    {analysis.run.notableObservations.map((item, idx) => (
                      <ListItem key={idx} disableGutters sx={{ py: 0.5 }}>
                        <ListItemIcon sx={{ minWidth: 26 }}>
                          <AlertCircle size={16} color="#F59E0B" />
                        </ListItemIcon>
                        <ListItemText primary={<Typography variant="body2" color="text.secondary">{item}</Typography>} />
                      </ListItem>
                    ))}
                  </List>
                </Box>
              )}

              <Box sx={{ pt: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid', borderColor: 'divider' }}>
                <Typography variant="caption" color="text.secondary">
                  Engine: <strong style={{ color: '#10B981' }}>{analysis.run.aiProvider}</strong>
                </Typography>
                <Chip icon={<FileCode2 size={12} />} label={`${analysis.tests.length} Tests Generated`} size="small" color="primary" variant="outlined" sx={{ height: 20, fontSize: '0.65rem' }} />
              </Box>
            </Box>
          )}
        </Card>
      </Grid>
    </Grid>
  );
}
