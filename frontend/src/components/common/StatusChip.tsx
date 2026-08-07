import { Chip } from '@mui/material';
import type { ProjectStatus } from '../../types/project';
import type { AnalysisStatus } from '../../types/analysis';

const STATUS_COLOR: Record<string, 'default' | 'info' | 'success' | 'error' | 'warning'> = {
  EXTRACTING: 'warning',
  UPLOADED: 'info',
  ANALYZING: 'warning',
  ANALYZED: 'success',
  FAILED: 'error',
  RUNNING: 'warning',
  COMPLETED: 'success',
};

export function StatusChip({ status }: { status: ProjectStatus | AnalysisStatus }) {
  return <Chip size="small" label={status} color={STATUS_COLOR[status] ?? 'default'} />;
}
