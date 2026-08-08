import { Chip, Tooltip } from '@mui/material';
import type { ProjectStatus } from '../../types/project';
import type { AnalysisStatus } from '../../types/analysis';
import { brand, status as statusColors } from '../../theme/palette';

/**
 * Status chip for projects and analysis runs.
 *
 * <p>Each status carries an explanation on hover. Bare status words like "UPLOADED" or "ANALYZED" leave
 * the user guessing what the system will do next, and the difference between "extracted but not analyzed"
 * and "analyzed" is exactly the kind of thing a QA tool must be unambiguous about.
 */
const STATUS_META: Record<string, { label: string; color: string; tooltip: string }> = {
  EXTRACTING: {
    label: 'Extracting',
    color: statusColors.warning,
    tooltip: 'The archive is being unpacked and indexed. Analysis cannot start until this finishes.',
  },
  UPLOADED: {
    label: 'Ready',
    color: brand.secondary,
    tooltip: 'Files are indexed and routes discovered, but no analysis has been run yet.',
  },
  ANALYZING: {
    label: 'Analyzing',
    color: brand.primary,
    tooltip: 'The analysis pipeline is running in the background. Progress updates automatically.',
  },
  ANALYZED: {
    label: 'Analyzed',
    color: statusColors.success,
    tooltip: 'Analysis completed. Findings, tests and the risk score reflect the most recent run.',
  },
  FAILED: {
    label: 'Failed',
    color: statusColors.error,
    tooltip: 'Processing or analysis failed. The reason is shown on the project page.',
  },
  RUNNING: { label: 'Running', color: brand.primary, tooltip: 'In progress.' },
  COMPLETED: { label: 'Completed', color: statusColors.success, tooltip: 'Finished successfully.' },
};

export function StatusChip({ status }: { status: ProjectStatus | AnalysisStatus }) {
  const meta = STATUS_META[status] ?? { label: status, color: statusColors.info, tooltip: status };
  return (
    <Tooltip title={meta.tooltip}>
      <Chip
        size="small"
        label={meta.label}
        sx={{
          fontWeight: 750,
          color: meta.color,
          bgcolor: `${meta.color}1F`,
          border: `1px solid ${meta.color}44`,
          cursor: 'help',
        }}
      />
    </Tooltip>
  );
}
