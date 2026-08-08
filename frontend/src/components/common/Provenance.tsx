import { Box, Chip, Tooltip, Typography } from '@mui/material';
import { Bot, CheckCircle2, CircleDashed, CircleSlash, FileSearch, XCircle, Zap } from 'lucide-react';
import type { ResultOrigin, TestExecutionStatus } from '../../types/analysis';
import { executionColors } from '../../theme/palette';

/**
 * Components that keep QPilot's central distinction visible in the UI: what was measured versus what
 * was suggested, and what was executed versus what merely exists.
 *
 * <p>These are not decorative badges. They are the mechanism by which a user can tell a verifiable
 * finding from an LLM's guess, and a passing test from an unrun one, at a glance — which is the
 * difference between a report they can act on and one they have to distrust wholesale.
 */

const ORIGIN_META: Record<ResultOrigin, { label: string; tooltip: string; color: 'secondary' | 'warning'; icon: typeof FileSearch }> = {
  STATIC_ANALYSIS: {
    label: 'Measured',
    tooltip:
      'Produced by scanning the project\'s real files. Cites the exact file and line, so you can open it and verify the finding yourself.',
    color: 'secondary',
    icon: FileSearch,
  },
  AI_SUGGESTION: {
    label: 'AI suggestion',
    tooltip:
      'Proposed by a language model from the project context. It carries no file/line evidence and has not been verified — review it before acting on it.',
    color: 'warning',
    icon: Bot,
  },
};

export function OriginChip({ origin, size = 'small' }: { origin: ResultOrigin; size?: 'small' | 'medium' }) {
  const meta = ORIGIN_META[origin] ?? ORIGIN_META.STATIC_ANALYSIS;
  const Icon = meta.icon;
  return (
    <Tooltip title={meta.tooltip}>
      <Chip
        size={size}
        variant="outlined"
        color={meta.color}
        icon={<Icon size={12} />}
        label={meta.label}
        sx={{ fontWeight: 700, cursor: 'help' }}
      />
    </Tooltip>
  );
}

const EXECUTION_META: Record<
  TestExecutionStatus,
  { label: string; tooltip: string; icon: typeof CheckCircle2 }
> = {
  EXECUTED_PASSED: {
    label: 'Passed',
    tooltip: 'QPilot sent this request to the live target and the response matched what the test expects.',
    icon: CheckCircle2,
  },
  EXECUTED_FAILED: {
    label: 'Failed',
    tooltip: 'QPilot sent this request to the live target and the response did not match what the test expects.',
    icon: XCircle,
  },
  EXECUTION_ERROR: {
    label: 'Error',
    tooltip: 'The request could not complete at all — no HTTP response was received (DNS, connection refused or timeout).',
    icon: CircleSlash,
  },
  GENERATED: {
    label: 'Generated',
    tooltip: 'The test code exists but has not been run. It has proved nothing yet — this is not a pass.',
    icon: Zap,
  },
  SKIPPED: {
    label: 'Skipped',
    tooltip: 'Runnable in principle, but a prerequisite was missing. See the detail for the exact reason.',
    icon: CircleDashed,
  },
  NOT_EXECUTABLE: {
    label: 'Not executable',
    tooltip:
      'QPilot cannot run this itself — unit tests need the project\'s own compiler, dependencies and test runner. The code is still complete and downloadable.',
    icon: CircleDashed,
  },
};

/**
 * Renders a test's real execution outcome.
 *
 * <p>Colour is taken from the shared palette, where GENERATED is deliberately *not* green: a generated
 * test has demonstrated nothing, and colouring it as a success is precisely the misreading this
 * component exists to prevent.
 */
export function ExecutionStatusChip({
  status,
  detail,
  size = 'small',
}: {
  status: TestExecutionStatus;
  detail?: string;
  size?: 'small' | 'medium';
}) {
  const meta = EXECUTION_META[status] ?? EXECUTION_META.GENERATED;
  const Icon = meta.icon;
  const color = executionColors[status] ?? executionColors.GENERATED;

  return (
    <Tooltip
      title={
        <Box>
          <Typography variant="caption" sx={{ fontWeight: 750, display: 'block' }}>
            {meta.tooltip}
          </Typography>
          {detail && (
            <Typography variant="caption" sx={{ display: 'block', mt: 0.75, opacity: 0.85 }}>
              {detail}
            </Typography>
          )}
        </Box>
      }
    >
      <Chip
        size={size}
        icon={<Icon size={12} color={color} />}
        label={meta.label}
        sx={{
          fontWeight: 750,
          cursor: 'help',
          color,
          bgcolor: `${color}1F`,
          border: `1px solid ${color}44`,
        }}
      />
    </Tooltip>
  );
}

/**
 * Section header that states, up front, whether the content below is measured or advisory.
 *
 * <p>Placed above every findings/insight block so the distinction cannot be missed by a user who scans
 * straight to the table.
 */
export function ProvenanceBanner({ origin, detail }: { origin: ResultOrigin; detail?: string }) {
  const meta = ORIGIN_META[origin];
  const Icon = meta.icon;
  const isAi = origin === 'AI_SUGGESTION';
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 1.25,
        px: 1.75,
        py: 1.25,
        borderRadius: 2.5,
        border: '1px solid',
        borderColor: isAi ? 'warning.main' : 'secondary.main',
        bgcolor: isAi ? 'rgba(241, 162, 43, 0.08)' : 'rgba(0, 194, 204, 0.07)',
      }}
    >
      <Icon size={16} style={{ marginTop: 2, flexShrink: 0 }} />
      <Box>
        <Typography variant="caption" sx={{ fontWeight: 800, display: 'block', letterSpacing: '0.02em' }}>
          {isAi ? 'AI SUGGESTIONS — UNVERIFIED' : 'MEASURED FROM YOUR FILES'}
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
          {detail ?? meta.tooltip}
        </Typography>
      </Box>
    </Box>
  );
}
