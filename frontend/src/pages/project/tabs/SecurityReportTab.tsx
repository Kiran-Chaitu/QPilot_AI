import { useMemo, useState } from 'react';
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
  TableHead,
  TableRow,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import { FileSearch, Info, ShieldCheck, Wrench } from 'lucide-react';
import { EmptyState } from '../../../components/common/StateViews';
import { OriginChip, ProvenanceBanner } from '../../../components/common/Provenance';
import { severityColors } from '../../../theme/palette';
import type { SecurityFindingResponse, Severity } from '../../../types/analysis';

const SEVERITY_ORDER: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

function SeverityChip({ severity }: { severity: Severity }) {
  const color = severityColors[severity];
  return (
    <Chip
      size="small"
      label={severity}
      sx={{ fontWeight: 800, color, bgcolor: `${color}1F`, border: `1px solid ${color}44`, minWidth: 74 }}
    />
  );
}

/**
 * Security findings, split by provenance.
 *
 * <p>Measured findings and AI suggestions are shown in separate sections rather than interleaved in one
 * table. Mixing them would let an unverified LLM guess inherit the credibility of a finding that cites a
 * real file and line — which is precisely the confusion the provenance model exists to prevent.
 */
export function SecurityReportTab({ findings }: { findings: SecurityFindingResponse[] }) {
  const [view, setView] = useState<'measured' | 'ai'>('measured');

  const measured = useMemo(() => findings.filter((finding) => finding.origin === 'STATIC_ANALYSIS'), [findings]);
  const suggested = useMemo(() => findings.filter((finding) => finding.origin === 'AI_SUGGESTION'), [findings]);

  const severityCounts = useMemo(() => {
    const counts = new Map<Severity, number>();
    measured.forEach((finding) => counts.set(finding.severity, (counts.get(finding.severity) ?? 0) + 1));
    return counts;
  }, [measured]);

  if (findings.length === 0) {
    return (
      <Card>
        <EmptyState
          icon={<ShieldCheck size={24} />}
          title="No security findings recorded"
          description="Either no analysis has run yet, or none of QPilot's static rules matched your source. Note that a clean result covers the rule set QPilot ships — it is not equivalent to a full security audit or a dependency CVE scan."
        />
      </Card>
    );
  }

  const active = view === 'measured' ? measured : suggested;

  return (
    <Stack spacing={2.5}>
      <Card sx={{ p: { xs: 2, md: 2.5 } }}>
        <Grid container spacing={2} sx={{ alignItems: 'center' }}>
          <Grid size={{ xs: 12, md: 7 }}>
            <Stack direction="row" spacing={1.5} sx={{ flexWrap: 'wrap', gap: 1 }}>
              {SEVERITY_ORDER.map((severity) => {
                const count = severityCounts.get(severity) ?? 0;
                const color = severityColors[severity];
                return (
                  <Paper
                    key={severity}
                    sx={{
                      px: 2,
                      py: 1.25,
                      borderRadius: 2.5,
                      border: '1px solid',
                      borderColor: count > 0 ? `${color}55` : 'divider',
                      bgcolor: count > 0 ? `${color}12` : 'transparent',
                      minWidth: 96,
                    }}
                  >
                    <Typography variant="h6" sx={{ fontWeight: 800, color: count > 0 ? color : 'text.disabled', lineHeight: 1.2 }}>
                      {count}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                      {severity}
                    </Typography>
                  </Paper>
                );
              })}
            </Stack>
          </Grid>
          <Grid size={{ xs: 12, md: 5 }}>
            <ToggleButtonGroup
              exclusive
              size="small"
              value={view}
              onChange={(_, value) => value && setView(value)}
              sx={{ width: '100%' }}
            >
              <ToggleButton value="measured" sx={{ flex: 1, fontWeight: 750, textTransform: 'none' }}>
                <FileSearch size={14} style={{ marginRight: 6 }} />
                Measured ({measured.length})
              </ToggleButton>
              <ToggleButton value="ai" sx={{ flex: 1, fontWeight: 750, textTransform: 'none' }} disabled={suggested.length === 0}>
                AI suggestions ({suggested.length})
              </ToggleButton>
            </ToggleButtonGroup>
          </Grid>
        </Grid>
      </Card>

      <ProvenanceBanner
        origin={view === 'measured' ? 'STATIC_ANALYSIS' : 'AI_SUGGESTION'}
        detail={
          view === 'measured'
            ? 'Each row below was produced by a rule matching your actual source. Open the cited file at the cited line and you will find the same text — that is what makes these verifiable.'
            : 'Proposed by a language model from the project context. These carry no file/line evidence and have not been verified. Treat them as leads to investigate, not as confirmed findings.'
        }
      />

      {active.length === 0 ? (
        <Card>
          <EmptyState
            dense
            title={view === 'measured' ? 'No measured findings' : 'No AI suggestions'}
            description={
              view === 'measured'
                ? 'None of the static rules matched your source.'
                : 'AI enrichment either did not run or contributed no additional findings.'
            }
          />
        </Card>
      ) : (
        <Card sx={{ p: { xs: 1.5, md: 2 } }}>
          <Box className="qp-scroll-x">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Severity</TableCell>
                  <TableCell>Category</TableCell>
                  {view === 'measured' && <TableCell>Evidence</TableCell>}
                  <TableCell>What it means</TableCell>
                  <TableCell>How to fix it</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {active.map((finding) => (
                  <TableRow key={finding.id} hover>
                    <TableCell sx={{ verticalAlign: 'top' }}>
                      <SeverityChip severity={finding.severity} />
                      {finding.occurrenceCount !== undefined && finding.occurrenceCount > 1 && (
                        <Tooltip title="Total times this rule matched across the project. One representative location is shown.">
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.75, cursor: 'help' }}>
                            ×{finding.occurrenceCount} places
                          </Typography>
                        </Tooltip>
                      )}
                    </TableCell>
                    <TableCell sx={{ verticalAlign: 'top', minWidth: 150 }}>
                      <Typography variant="body2" sx={{ fontWeight: 750 }}>
                        {finding.category.replace(/_/g, ' ')}
                      </Typography>
                      {finding.ruleId && (
                        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'var(--font-mono)' }}>
                          {finding.ruleId}
                        </Typography>
                      )}
                      {view === 'ai' && (
                        <Box sx={{ mt: 0.75 }}>
                          <OriginChip origin={finding.origin} />
                        </Box>
                      )}
                    </TableCell>
                    {view === 'measured' && (
                      <TableCell sx={{ verticalAlign: 'top', maxWidth: 300 }}>
                        <Typography variant="caption" sx={{ fontFamily: 'var(--font-mono)', fontWeight: 700, display: 'block', overflowWrap: 'anywhere' }}>
                          {finding.location}
                          {finding.lineNumber ? `:${finding.lineNumber}` : ''}
                        </Typography>
                        {finding.evidence && (
                          <Box
                            component="code"
                            sx={{ display: 'block', mt: 0.75, fontSize: '0.7rem', lineHeight: 1.5, whiteSpace: 'pre-wrap' }}
                          >
                            {finding.evidence}
                          </Box>
                        )}
                      </TableCell>
                    )}
                    <TableCell sx={{ verticalAlign: 'top', maxWidth: 340 }}>
                      <Typography variant="body2" color="text.secondary">
                        {finding.description}
                      </Typography>
                      {view === 'ai' && finding.location && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5, fontStyle: 'italic' }}>
                          Suggested location: {finding.location}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell sx={{ verticalAlign: 'top', maxWidth: 340 }}>
                      <Stack direction="row" spacing={0.75} sx={{ alignItems: 'flex-start' }}>
                        <Wrench size={13} style={{ marginTop: 3, flexShrink: 0, opacity: 0.6 }} />
                        <Typography variant="caption" color="text.secondary">
                          {finding.recommendation}
                        </Typography>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        </Card>
      )}

      <Alert severity="info" variant="outlined" icon={<Info size={18} />} sx={{ borderRadius: 3 }}>
        <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
          What this scan does and does not cover
        </Typography>
        <Typography variant="caption" color="text.secondary">
          These are lexical pattern checks over your real files, not interprocedural dataflow analysis, so they can
          miss issues that span functions. Dependency CVE matching is not included — no advisory database ships with
          QPilot, so run your ecosystem&apos;s own auditor (npm audit, mvn dependency-check, pip-audit) alongside this.
        </Typography>
      </Alert>
    </Stack>
  );
}
