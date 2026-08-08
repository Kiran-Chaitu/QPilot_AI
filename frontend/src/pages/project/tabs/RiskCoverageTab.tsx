import {
  Alert,
  Box,
  Card,
  Chip,
  Grid,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import { Calculator, Info, ShieldAlert, Target, TrendingUp } from 'lucide-react';
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts';
import { EmptyState } from '../../../components/common/StateViews';
import { riskColor, riskLabel, status as statusColors } from '../../../theme/palette';
import type { RiskAssessmentResponse } from '../../../types/analysis';

function Gauge({ value, color, caption, label }: { value: number; color: string; caption: string; label: string }) {
  const data = [
    { name: label, value },
    { name: 'remainder', value: Math.max(0, 100 - value) },
  ];
  return (
    <Box sx={{ position: 'relative', width: '100%', height: 190, display: 'grid', placeItems: 'center' }}>
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            innerRadius={62}
            outerRadius={84}
            startAngle={90}
            endAngle={-270}
            paddingAngle={1.5}
            stroke="none"
            isAnimationActive={false}
          >
            <Cell fill={color} />
            <Cell fill="var(--qp-border)" />
          </Pie>
        </PieChart>
      </ResponsiveContainer>
      <Box sx={{ position: 'absolute', textAlign: 'center', pointerEvents: 'none' }}>
        <Typography variant="h3" sx={{ color, fontWeight: 800, lineHeight: 1 }}>
          {value}
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
          {caption}
        </Typography>
      </Box>
    </Box>
  );
}

/**
 * Risk and test-surface view.
 *
 * <p>Two things are load-bearing here. The score is always shown next to the arithmetic that produced it,
 * so a user can disagree with a specific contribution rather than dismissing the number as arbitrary. And
 * the tested-surface percentage is always labelled with what it actually measured — it is explicitly not
 * executed line coverage, which QPilot does not measure and therefore does not claim.
 */
export function RiskCoverageTab({ risk }: { risk?: RiskAssessmentResponse }) {
  if (!risk) {
    return (
      <Card>
        <EmptyState
          icon={<ShieldAlert size={24} />}
          title="No risk assessment yet"
          description="Run an analysis to compute a risk score from measured facts about your project — findings by severity, and how much of the discovered test surface has tests."
        />
      </Card>
    );
  }

  const color = riskColor(risk.score);
  const measured = risk.measured;

  return (
    <Stack spacing={2.5}>
      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ p: 2.5, height: '100%', textAlign: 'center' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'center', mb: 0.5 }}>
              <ShieldAlert size={18} color={color} />
              <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                Risk score
              </Typography>
            </Stack>
            <Gauge value={risk.score} color={color} caption="OUT OF 100" label="Risk" />
            <Chip
              label={riskLabel(risk.score)}
              sx={{ bgcolor: `${color}1F`, color, fontWeight: 800, border: `1px solid ${color}44` }}
            />
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ p: 2.5, height: '100%', textAlign: 'center' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'center', mb: 0.5 }}>
              <Target size={18} color={statusColors.success} />
              <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                Tested surface
              </Typography>
            </Stack>
            <Gauge value={risk.testedSurfacePercent} color={statusColors.success} caption="MEASURED" label="Tested" />
            <Tooltip title={risk.testedSurfaceBasis ?? ''}>
              <Chip
                variant="outlined"
                label="What does this measure?"
                sx={{ fontWeight: 700, cursor: 'help' }}
              />
            </Tooltip>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ p: 2.5, height: '100%' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
              <Calculator size={18} />
              <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                How the score was calculated
              </Typography>
            </Stack>
            <Stack spacing={0.75}>
              {risk.scoreBreakdown.map((line, index) => (
                <Typography
                  key={index}
                  variant="caption"
                  sx={{
                    fontFamily: 'var(--font-mono)',
                    fontSize: '0.72rem',
                    fontWeight: index === risk.scoreBreakdown.length - 1 ? 800 : 500,
                    color: index === risk.scoreBreakdown.length - 1 ? color : 'text.secondary',
                  }}
                >
                  {line}
                </Typography>
              ))}
            </Stack>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
              A fixed formula over counted facts — no model estimated this number, and re-running the analysis on the
              same code produces the same score.
            </Typography>
          </Card>
        </Grid>
      </Grid>

      {/* What "tested surface" means, stated in full rather than hidden in a tooltip. */}
      <Alert severity="info" variant="outlined" icon={<Info size={18} />} sx={{ borderRadius: 3 }}>
        <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
          This is not executed code coverage
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {risk.testedSurfaceBasis} Real line coverage requires running your test suite under an instrumentation agent,
          which QPilot does not do for uploaded archives.
        </Typography>
      </Alert>

      <Grid container spacing={2.5}>
        {measured && (
          <Grid size={{ xs: 12, md: 5 }}>
            <Card sx={{ p: 2.5, height: '100%' }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                <TrendingUp size={18} />
                <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                  Measured inputs
                </Typography>
              </Stack>
              <Table size="small">
                <TableBody>
                  {[
                    ['Source files', measured.sourceFileCount],
                    ['Test files', measured.testFileCount],
                    ['Non-blank lines of code', measured.totalLinesOfCode],
                    ['HTTP endpoints discovered', measured.endpointCount],
                    ['Endpoints referenced by tests', measured.endpointsReferencedByTests],
                  ].map(([label, value]) => (
                    <TableRow key={String(label)}>
                      <TableCell sx={{ border: 'none', pl: 0, color: 'text.secondary' }}>{label}</TableCell>
                      <TableCell align="right" sx={{ border: 'none', pr: 0, fontWeight: 750 }}>
                        {String(value)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.75, mt: 1.5 }}>
                {[
                  ['CRITICAL', measured.criticalFindingCount],
                  ['HIGH', measured.highFindingCount],
                  ['MEDIUM', measured.mediumFindingCount],
                  ['LOW', measured.lowFindingCount],
                ].map(([label, count]) => (
                  <Chip key={String(label)} size="small" variant="outlined" label={`${label}: ${count}`} sx={{ fontWeight: 700 }} />
                ))}
              </Stack>
            </Card>
          </Grid>
        )}

        <Grid size={{ xs: 12, md: measured ? 7 : 12 }}>
          <Stack spacing={2.5} sx={{ height: '100%' }}>
            <Card sx={{ p: 2.5 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 750, mb: 1.5 }}>
                Risk drivers
              </Typography>
              <Stack spacing={1}>
                {risk.reasons.map((reason, index) => (
                  <Stack key={index} direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                    <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: color, mt: '7px', flexShrink: 0 }} />
                    <Typography variant="body2" color="text.secondary">
                      {reason}
                    </Typography>
                  </Stack>
                ))}
              </Stack>
            </Card>

            {risk.coverageGaps.length > 0 && (
              <Card sx={{ p: 2.5, flexGrow: 1 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                  <Target size={18} color={statusColors.warning} />
                  <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                    Untested endpoints &amp; areas
                  </Typography>
                  <Chip size="small" variant="outlined" label={risk.coverageGaps.length} />
                </Stack>
                <Box sx={{ maxHeight: 240, overflowY: 'auto' }}>
                  <Stack spacing={0.75}>
                    {risk.coverageGaps.map((gap, index) => (
                      <Typography
                        key={index}
                        variant="caption"
                        sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.73rem', overflowWrap: 'anywhere' }}
                      >
                        • {gap}
                      </Typography>
                    ))}
                  </Stack>
                </Box>
              </Card>
            )}
          </Stack>
        </Grid>
      </Grid>

      {risk.unavailableChecks.length > 0 && (
        <Card sx={{ p: 2.5 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
            <Info size={18} />
            <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
              Checks not performed
            </Typography>
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
            Listed explicitly, because an absent section would read as a clean result.
          </Typography>
          <Stack spacing={1}>
            {risk.unavailableChecks.map((check, index) => (
              <Paper key={index} sx={{ p: 1.5, borderRadius: 2.5, bgcolor: 'action.hover', border: '1px solid', borderColor: 'divider' }}>
                <Typography variant="caption" color="text.secondary">
                  {check}
                </Typography>
              </Paper>
            ))}
          </Stack>
        </Card>
      )}
    </Stack>
  );
}
