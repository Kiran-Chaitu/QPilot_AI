import { Chip } from '@mui/material';
import type { Severity } from '../../types/analysis';

const SEVERITY_COLOR: Record<Severity, 'success' | 'warning' | 'error' | 'default'> = {
  LOW: 'success',
  MEDIUM: 'warning',
  HIGH: 'error',
  CRITICAL: 'error',
};

export function SeverityChip({ severity }: { severity: Severity }) {
  return <Chip size="small" label={severity} color={SEVERITY_COLOR[severity]} variant={severity === 'CRITICAL' ? 'filled' : 'outlined'} />;
}
