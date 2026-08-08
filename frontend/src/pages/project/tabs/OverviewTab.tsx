import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Grid,
  LinearProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { Bot, Code2, FileSearch, Info, Layers, Play, Sparkles } from 'lucide-react';
import { EmptyState, NotAvailable } from '../../../components/common/StateViews';
import { brand, status as statusColors } from '../../../theme/palette';
import type { ProjectResponse, ProjectStructureSummary } from '../../../types/project';
import type { AnalysisResultResponse } from '../../../types/analysis';

const METHOD_COLORS: Record<string, string> = {
  GET: statusColors.success,
  POST: brand.primary,
  PUT: statusColors.warning,
  PATCH: statusColors.warning,
  DELETE: statusColors.error,
};

function StatTile({ label, value, hint }: { label: string; value: React.ReactNode; hint?: string }) {
  return (
    <Paper sx={{ p: 1.75, borderRadius: 3, bgcolor: 'action.hover', border: '1px solid', borderColor: 'divider', height: '100%' }}>
      <Typography variant="overline" color="text.secondary" sx={{ display: 'block', lineHeight: 1.4 }}>
        {label}
      </Typography>
      <Typography variant="h6" sx={{ fontWeight: 800, my: 0.25 }}>
        {value}
      </Typography>
      {hint && (
        <Typography variant="caption" color="text.secondary">
          {hint}
        </Typography>
      )}
    </Paper>
  );
}

export function OverviewTab({
  project,
  structure,
  analysis,
  onAnalyze,
  isAnalyzing,
}: {
  project: ProjectResponse;
  structure: ProjectStructureSummary | null;
  analysis: AnalysisResultResponse | null;
  onAnalyze: () => void;
  isAnalyzing: boolean;
}) {
  const run = analysis?.run;
  const isUrlProject = project.sourceType === 'WEBSITE_URL' || project.sourceType === 'API_URL';

  return (
    <Grid container spacing={2.5}>
      {/* ── Structure (real counts) ────────────────────────────────────── */}
      <Grid size={{ xs: 12, lg: 7 }}>
        <Stack spacing={2.5}>
          <Card sx={{ p: { xs: 2, md: 2.5 } }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
              <Layers size={18} color={brand.primary} />
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                Project structure
              </Typography>
              <Chip size="small" variant="outlined" color="secondary" label="Counted from your files" sx={{ fontWeight: 700 }} />
            </Stack>

            <Grid container spacing={1.5} sx={{ mb: 2.5 }}>
              <Grid size={{ xs: 6, sm: 3 }}>
                <StatTile label="Primary language" value={structure?.primaryLanguage ?? 'Unknown'} />
              </Grid>
              <Grid size={{ xs: 6, sm: 3 }}>
                <StatTile
                  label="Files indexed"
                  value={
                    isUrlProject && (structure?.totalFiles ?? 0) === 0 ? (
                      <NotAvailable reason="This is a URL-based project — no source archive was downloaded, so there are no files to count. Upload a ZIP to enable source metrics." />
                    ) : (
                      structure?.totalFiles ?? 0
                    )
                  }
                />
              </Grid>
              <Grid size={{ xs: 6, sm: 3 }}>
                <StatTile label="Endpoints found" value={structure?.endpoints.length ?? 0} hint="by route scanning" />
              </Grid>
              <Grid size={{ xs: 6, sm: 3 }}>
                <StatTile
                  label="Dependencies"
                  value={
                    isUrlProject && (structure?.dependencies.length ?? 0) === 0 ? (
                      <NotAvailable reason="Dependency manifests are not reachable over HTTP. Upload the source archive to parse them." />
                    ) : (
                      structure?.dependencies.length ?? 0
                    )
                  }
                  hint="from manifests"
                />
              </Grid>
            </Grid>

            {structure && Object.keys(structure.languageBreakdown).length > 0 && (
              <Box sx={{ mb: 2.5 }}>
                <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
                  Language distribution
                </Typography>
                <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.75 }}>
                  {Object.entries(structure.languageBreakdown)
                    .sort(([, a], [, b]) => b - a)
                    .map(([language, count]) => (
                      <Chip key={language} size="small" label={`${language} · ${count}`} sx={{ fontWeight: 700 }} />
                    ))}
                </Stack>
              </Box>
            )}

            {structure && structure.dependencies.length > 0 && (
              <Box sx={{ mb: 2.5 }}>
                <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
                  Declared dependencies ({structure.dependencies.length})
                </Typography>
                <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.75, maxHeight: 120, overflowY: 'auto' }}>
                  {structure.dependencies.slice(0, 40).map((dependency) => (
                    <Chip
                      key={dependency}
                      size="small"
                      variant="outlined"
                      label={dependency}
                      sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.68rem' }}
                    />
                  ))}
                </Stack>
              </Box>
            )}

            {project.discoveryNotes && (
              <Alert severity="info" variant="outlined" icon={<Info size={18} />} sx={{ borderRadius: 2.5 }}>
                <Typography variant="caption" sx={{ fontWeight: 750, display: 'block', mb: 0.5 }}>
                  What discovery found
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {project.discoveryNotes}
                </Typography>
              </Alert>
            )}
          </Card>

          <Card sx={{ p: { xs: 2, md: 2.5 } }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
              <Code2 size={18} color={brand.secondary} />
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                Discovered routes
              </Typography>
              <Chip size="small" variant="outlined" label={structure?.endpoints.length ?? 0} />
            </Stack>

            {!structure || structure.endpoints.length === 0 ? (
              <EmptyState
                dense
                icon={<Code2 size={22} />}
                title="No routes discovered"
                description="QPilot found no HTTP routes. The project may not expose any, or it may use a framework whose routing syntax is not among the supported patterns (Spring MVC, Express, Flask/FastAPI). Attaching an OpenAPI document gives QPilot an authoritative route list."
              />
            ) : (
              <Box className="qp-scroll-x" sx={{ maxHeight: 400, overflowY: 'auto' }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Method</TableCell>
                      <TableCell>Path</TableCell>
                      <TableCell>Declared in</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {structure.endpoints.map((endpoint, index) => {
                      const color = METHOD_COLORS[endpoint.httpMethod] ?? brand.secondary;
                      return (
                        <TableRow key={`${endpoint.httpMethod}-${endpoint.path}-${index}`} hover>
                          <TableCell sx={{ width: 92 }}>
                            <Chip
                              size="small"
                              label={endpoint.httpMethod}
                              sx={{ fontWeight: 800, fontSize: '0.66rem', color, bgcolor: `${color}1F`, border: `1px solid ${color}3D` }}
                            />
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" sx={{ fontFamily: 'var(--font-mono)', fontWeight: 650, overflowWrap: 'anywhere' }}>
                              {endpoint.path}
                            </Typography>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 240 }}>
                            <Typography variant="caption" color="text.secondary" className="qp-truncate" sx={{ fontFamily: 'var(--font-mono)', display: 'block' }}>
                              {endpoint.sourceFile}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </Box>
            )}
          </Card>
        </Stack>
      </Grid>

      {/* ── Analysis output ───────────────────────────────────────────── */}
      <Grid size={{ xs: 12, lg: 5 }}>
        <Stack spacing={2.5}>
          <Card sx={{ p: { xs: 2, md: 2.5 } }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <FileSearch size={18} color={brand.secondary} />
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                  Static analysis
                </Typography>
              </Stack>
              <Button
                variant={analysis ? 'outlined' : 'contained'}
                size="small"
                startIcon={isAnalyzing ? <CircularProgress size={14} color="inherit" /> : <Play size={14} />}
                onClick={onAnalyze}
                disabled={isAnalyzing}
                sx={{ fontWeight: 750 }}
              >
                {isAnalyzing ? 'Analyzing…' : analysis ? 'Re-run' : 'Run analysis'}
              </Button>
            </Stack>

            {isAnalyzing && run && (
              <Box sx={{ mb: 2 }}>
                <LinearProgress variant="determinate" value={run.progressPercent} />
                <Typography variant="caption" color="text.secondary" sx={{ mt: 0.75, display: 'block' }}>
                  {run.progressPercent}% — {run.currentStage}
                </Typography>
              </Box>
            )}

            {!analysis ? (
              <EmptyState
                dense
                icon={<Sparkles size={22} />}
                title="Not analyzed yet"
                description="QPilot will scan your real files, match security rules with file/line evidence, count the test surface and compute a risk score from those counts."
              />
            ) : (
              <Stack spacing={2}>
                {run?.staticSummary && (
                  <Paper sx={{ p: 1.75, borderRadius: 2.5, bgcolor: 'rgba(0, 194, 204, 0.07)', border: '1px solid rgba(0, 194, 204, 0.28)' }}>
                    <Typography variant="overline" sx={{ color: brand.secondary, display: 'block', mb: 0.5 }}>
                      MEASURED SUMMARY
                    </Typography>
                    <Typography variant="body2">{run.staticSummary}</Typography>
                  </Paper>
                )}

                {run && run.observations.length > 0 && (
                  <Box>
                    <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
                      Observations
                    </Typography>
                    <Stack spacing={0.75}>
                      {run.observations.map((observation, index) => (
                        <Stack key={index} direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                          <Box sx={{ width: 5, height: 5, borderRadius: '50%', bgcolor: 'secondary.main', mt: '7px', flexShrink: 0 }} />
                          <Typography variant="caption" color="text.secondary">
                            {observation}
                          </Typography>
                        </Stack>
                      ))}
                    </Stack>
                  </Box>
                )}

                {run?.errorMessage && (
                  <Alert severity="error" variant="outlined" sx={{ borderRadius: 2.5 }}>
                    <Typography variant="caption">{run.errorMessage}</Typography>
                  </Alert>
                )}
              </Stack>
            )}
          </Card>

          {/* AI panel — always present, always explicit about whether it ran. */}
          <Card sx={{ p: { xs: 2, md: 2.5 } }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
              <Bot size={18} color={statusColors.warning} />
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                AI narrative
              </Typography>
              {run?.aiEnabled ? (
                <Chip size="small" variant="outlined" color="warning" label="Advisory" sx={{ fontWeight: 700 }} />
              ) : (
                <Chip size="small" variant="outlined" label="Not applied" sx={{ fontWeight: 700 }} />
              )}
            </Stack>

            {!run ? (
              <Typography variant="body2" color="text.secondary">
                Run an analysis first.
              </Typography>
            ) : run.aiEnabled && run.aiSummary ? (
              <Stack spacing={1.75}>
                <Typography variant="body2">{run.aiSummary}</Typography>
                {run.aiKeyResponsibilities.length > 0 && (
                  <Box>
                    <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                      Key responsibilities (AI)
                    </Typography>
                    <Stack spacing={0.5}>
                      {run.aiKeyResponsibilities.map((item, index) => (
                        <Typography key={index} variant="caption" color="text.secondary">
                          • {item}
                        </Typography>
                      ))}
                    </Stack>
                  </Box>
                )}
                <Alert severity="warning" variant="outlined" sx={{ borderRadius: 2.5 }}>
                  <Typography variant="caption">
                    Produced by {run.aiProvider}. This is an interpretation, not a measurement — the numbers elsewhere
                    on this page were computed independently of it.
                  </Typography>
                </Alert>
              </Stack>
            ) : (
              <Alert severity="info" variant="outlined" icon={<Info size={18} />} sx={{ borderRadius: 2.5 }}>
                <Typography variant="caption" sx={{ fontWeight: 750, display: 'block', mb: 0.5 }}>
                  No AI narrative for this run
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {run.aiStatus ?? 'AI enrichment did not run.'}
                </Typography>
              </Alert>
            )}
          </Card>
        </Stack>
      </Grid>
    </Grid>
  );
}
