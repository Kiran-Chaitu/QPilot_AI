import { useMemo, useState } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Grid,
  Paper,
  Stack,
  Tab,
  Tabs,
  Tooltip,
  Typography,
} from '@mui/material';
import { Check, ChevronDown, Copy, Download, FileCode, Info, PlayCircle } from 'lucide-react';
import { useToast } from '../../../context/ToastContext';
import { EmptyState } from '../../../components/common/StateViews';
import { ExecutionStatusChip, OriginChip } from '../../../components/common/Provenance';
import { executeProjectTests } from '../../../api/analysisApi';
import { extractErrorMessage } from '../../../api/httpClient';
import { executionColors, status as statusColors } from '../../../theme/palette';
import type { GeneratedTestResponse, TestExecutionStatus, TestType } from '../../../types/analysis';

const TYPE_LABELS: Record<TestType, string> = {
  UNIT: 'Unit',
  API: 'API',
  INTEGRATION: 'Integration',
  SECURITY: 'Security',
  EDGE_CASE: 'Edge case',
};

/** File extension for a downloaded test, inferred from the framework it was written for. */
function extensionFor(framework?: string): string {
  const lower = (framework ?? '').toLowerCase();
  if (lower.includes('pytest') || lower.includes('python')) return '.py';
  if (lower.includes('jest') || lower.includes('vitest') || lower.includes('playwright') || lower.includes('cypress')) return '.ts';
  if (lower.includes('junit') || lower.includes('assured') || lower.includes('assertj')) return '.java';
  if (lower.includes('xunit')) return '.cs';
  if (lower.includes('go ')) return '_test.go';
  return '.txt';
}

function CountTile({ label, value, color, tooltip }: { label: string; value: number; color: string; tooltip: string }) {
  return (
    <Tooltip title={tooltip}>
      <Paper
        sx={{
          p: 1.75,
          borderRadius: 3,
          border: '1px solid',
          borderColor: 'divider',
          textAlign: 'center',
          cursor: 'help',
          height: '100%',
        }}
      >
        <Typography variant="h5" sx={{ fontWeight: 800, color, lineHeight: 1.2 }}>
          {value}
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 650 }}>
          {label}
        </Typography>
      </Paper>
    </Tooltip>
  );
}

export function GeneratedTestsTab({
  tests,
  projectId,
  onTestsUpdated,
}: {
  tests: GeneratedTestResponse[];
  projectId?: number;
  onTestsUpdated?: () => void;
}) {
  const { showSuccess, showError, showInfo } = useToast();
  const [activeType, setActiveType] = useState<TestType | 'ALL'>('ALL');
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [isExecuting, setIsExecuting] = useState(false);

  const counts = useMemo(() => {
    const byType = new Map<TestType, number>();
    const byStatus = new Map<TestExecutionStatus, number>();
    tests.forEach((test) => {
      byType.set(test.type, (byType.get(test.type) ?? 0) + 1);
      byStatus.set(test.executionStatus, (byStatus.get(test.executionStatus) ?? 0) + 1);
    });
    return { byType, byStatus };
  }, [tests]);

  const filtered = useMemo(
    () => (activeType === 'ALL' ? tests : tests.filter((test) => test.type === activeType)),
    [tests, activeType],
  );

  const executed =
    (counts.byStatus.get('EXECUTED_PASSED') ?? 0) +
    (counts.byStatus.get('EXECUTED_FAILED') ?? 0) +
    (counts.byStatus.get('EXECUTION_ERROR') ?? 0);
  const executableCount = tests.filter((test) => test.requestPath).length;

  const handleCopy = async (test: GeneratedTestResponse) => {
    try {
      await navigator.clipboard.writeText(test.code);
      setCopiedId(test.id);
      showSuccess('Test code copied.');
      setTimeout(() => setCopiedId(null), 2000);
    } catch {
      showError('The browser blocked clipboard access. Select the code and copy it manually.');
    }
  };

  const handleDownload = (test: GeneratedTestResponse) => {
    const safeName = (test.title || 'test').replace(/[^A-Za-z0-9_-]/g, '_');
    const blob = new Blob([test.code], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${safeName}${extensionFor(test.framework)}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  const handleExecute = async () => {
    if (!projectId) return;
    setIsExecuting(true);
    try {
      const summary = await executeProjectTests(projectId);
      if (summary.executed === 0) {
        showInfo('No tests could be executed. Each test now carries the reason why — open one to see it.');
      } else {
        showSuccess(
          `${summary.executed} test(s) executed against ${summary.baseUrl}: ${summary.passed} passed, ${summary.failed} failed, ${summary.errored} errored.`,
        );
      }
      onTestsUpdated?.();
    } catch (err) {
      showError(extractErrorMessage(err, 'Test execution failed.'));
    } finally {
      setIsExecuting(false);
    }
  };

  if (tests.length === 0) {
    return (
      <Card>
        <EmptyState
          icon={<FileCode size={24} />}
          title="No tests generated yet"
          description="Run an analysis on this project. QPilot derives tests from the routes, classes and functions it actually finds in your code — if it discovers no routes, it generates no API tests and tells you why rather than inventing some."
        />
      </Card>
    );
  }

  return (
    <Stack spacing={2.5}>
      {/* Execution summary — the numbers that are easiest to misread, stated explicitly. */}
      <Card sx={{ p: { xs: 2, md: 2.5 } }}>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={2}
          sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, mb: 2 }}
        >
          <Box>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              {tests.length} test{tests.length === 1 ? '' : 's'} generated
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {executed === 0
                ? 'None have been executed yet, so none can be reported as passing.'
                : `${executed} of them have actually been executed against a live target.`}
            </Typography>
          </Box>
          {projectId && executableCount > 0 && (
            <Button
              variant="contained"
              startIcon={isExecuting ? <CircularProgress size={16} color="inherit" /> : <PlayCircle size={18} />}
              onClick={handleExecute}
              disabled={isExecuting}
              sx={{ fontWeight: 750 }}
            >
              {isExecuting ? 'Executing…' : `Execute ${executableCount} runnable test(s)`}
            </Button>
          )}
        </Stack>

        <Grid container spacing={1.5} className="qp-stagger">
          <Grid size={{ xs: 6, sm: 4, md: 2 }}>
            <CountTile
              label="Passed"
              value={counts.byStatus.get('EXECUTED_PASSED') ?? 0}
              color={executionColors.EXECUTED_PASSED}
              tooltip="Executed against the live target and the response matched what the test expects. This is the only count that means a test passed."
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 2 }}>
            <CountTile
              label="Failed"
              value={counts.byStatus.get('EXECUTED_FAILED') ?? 0}
              color={executionColors.EXECUTED_FAILED}
              tooltip="Executed and the response did not match. A real, observed failure."
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 2 }}>
            <CountTile
              label="Errored"
              value={counts.byStatus.get('EXECUTION_ERROR') ?? 0}
              color={executionColors.EXECUTION_ERROR}
              tooltip="The request could not complete at all — no HTTP response was received."
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 2 }}>
            <CountTile
              label="Generated"
              value={counts.byStatus.get('GENERATED') ?? 0}
              color={executionColors.GENERATED}
              tooltip="Code exists but has not been run. It has proved nothing — deliberately not coloured as a success."
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 2 }}>
            <CountTile
              label="Skipped"
              value={counts.byStatus.get('SKIPPED') ?? 0}
              color={executionColors.SKIPPED}
              tooltip="Runnable in principle, but a prerequisite was missing — usually no live target URL on the project."
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 2 }}>
            <CountTile
              label="Not executable"
              value={counts.byStatus.get('NOT_EXECUTABLE') ?? 0}
              color={executionColors.NOT_EXECUTABLE}
              tooltip="QPilot cannot run these itself — unit tests need your project's own compiler and test runner. The code is complete and downloadable."
            />
          </Grid>
        </Grid>

        {executed === 0 && (
          <Alert severity="info" variant="outlined" icon={<Info size={18} />} sx={{ mt: 2, borderRadius: 2.5 }}>
            <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
              Generated is not the same as passing
            </Typography>
            <Typography variant="caption" color="text.secondary">
              QPilot will not report a test as passing unless it ran it and observed the response. Set a target URL on
              this project to have the HTTP-level tests executed for real.
            </Typography>
          </Alert>
        )}
      </Card>

      <Tabs
        value={activeType}
        onChange={(_, value) => setActiveType(value)}
        variant="scrollable"
        scrollButtons="auto"
        sx={{ borderBottom: '1px solid', borderColor: 'divider' }}
      >
        <Tab value="ALL" label={`All (${tests.length})`} />
        {(Object.keys(TYPE_LABELS) as TestType[])
          .filter((type) => counts.byType.has(type))
          .map((type) => (
            <Tab key={type} value={type} label={`${TYPE_LABELS[type]} (${counts.byType.get(type)})`} />
          ))}
      </Tabs>

      <Stack spacing={1.5}>
        {filtered.map((test) => (
          <Accordion
            key={test.id}
            disableGutters
            sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider', overflow: 'hidden', bgcolor: 'background.paper' }}
          >
            <AccordionSummary expandIcon={<ChevronDown size={18} />}>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1}
                sx={{ width: '100%', pr: 1.5, alignItems: { sm: 'center' }, minWidth: 0 }}
              >
                <Chip size="small" variant="outlined" label={TYPE_LABELS[test.type]} sx={{ fontWeight: 750, flexShrink: 0 }} />
                <Typography
                  sx={{ flexGrow: 1, fontWeight: 700, fontSize: '0.9rem', minWidth: 0, overflowWrap: 'anywhere' }}
                >
                  {test.title}
                </Typography>
                <Stack direction="row" spacing={0.75} sx={{ flexShrink: 0 }}>
                  <ExecutionStatusChip status={test.executionStatus} detail={test.executionDetail} />
                  <OriginChip origin={test.origin} />
                </Stack>
              </Stack>
            </AccordionSummary>
            <AccordionDetails sx={{ p: 2.5, bgcolor: 'action.hover' }}>
              <Stack spacing={2}>
                {/* The execution record: what actually happened, or why nothing did. */}
                {test.executionDetail && (
                  <Alert
                    severity={
                      test.executionStatus === 'EXECUTED_PASSED'
                        ? 'success'
                        : test.executionStatus === 'EXECUTED_FAILED' || test.executionStatus === 'EXECUTION_ERROR'
                          ? 'error'
                          : 'info'
                    }
                    variant="outlined"
                    sx={{ borderRadius: 2.5 }}
                  >
                    <Typography variant="caption" sx={{ fontWeight: 750, display: 'block', mb: 0.25 }}>
                      {test.lastExecutedAt
                        ? `Executed ${new Date(test.lastExecutedAt).toLocaleString()}`
                        : 'Not executed'}
                    </Typography>
                    <Typography variant="caption" sx={{ overflowWrap: 'anywhere' }}>
                      {test.executionDetail}
                    </Typography>
                  </Alert>
                )}

                <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', gap: 1.5 }}>
                  {test.targetName && (
                    <Box>
                      <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
                        Target
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem' }}>
                        {test.targetName}
                      </Typography>
                    </Box>
                  )}
                  {test.framework && (
                    <Box>
                      <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
                        Framework
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 650 }}>
                        {test.framework}
                      </Typography>
                    </Box>
                  )}
                  {test.expectedStatusCodes && (
                    <Box>
                      <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
                        Passes on status
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem' }}>
                        {test.expectedStatusCodes}
                      </Typography>
                    </Box>
                  )}
                  {test.observedHttpStatus !== undefined && test.observedHttpStatus !== null && (
                    <Box>
                      <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
                        Observed
                      </Typography>
                      <Typography
                        variant="body2"
                        sx={{
                          fontFamily: 'var(--font-mono)',
                          fontSize: '0.8rem',
                          fontWeight: 750,
                          color: test.executionStatus === 'EXECUTED_PASSED' ? statusColors.success : statusColors.error,
                        }}
                      >
                        HTTP {test.observedHttpStatus}
                        {test.executionLatencyMs !== undefined ? ` · ${test.executionLatencyMs}ms` : ''}
                      </Typography>
                    </Box>
                  )}
                </Stack>

                {test.description && (
                  <Typography variant="body2" color="text.secondary">
                    {test.description}
                  </Typography>
                )}

                <Box>
                  <Stack direction="row" spacing={1} sx={{ mb: 1, justifyContent: 'flex-end' }}>
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={copiedId === test.id ? <Check size={14} /> : <Copy size={14} />}
                      onClick={() => handleCopy(test)}
                      sx={{ fontWeight: 700 }}
                    >
                      {copiedId === test.id ? 'Copied' : 'Copy'}
                    </Button>
                    <Button size="small" variant="contained" startIcon={<Download size={14} />} onClick={() => handleDownload(test)} sx={{ fontWeight: 700 }}>
                      Download
                    </Button>
                  </Stack>
                  <Box component="pre" className="qp-scroll-x">
                    {test.code}
                  </Box>
                </Box>
              </Stack>
            </AccordionDetails>
          </Accordion>
        ))}
      </Stack>
    </Stack>
  );
}
