import {
  Box,
  Card,
  Chip,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { ShieldAlert, ShieldCheck, AlertTriangle } from 'lucide-react';
import { SeverityChip } from '../../../components/common/SeverityChip';
import type { SecurityFindingResponse } from '../../../types/analysis';

export function SecurityReportTab({ findings }: { findings: SecurityFindingResponse[] }) {
  if (findings.length === 0) {
    return (
      <Box sx={{ textAlign: 'center', py: 8 }}>
        <ShieldCheck size={48} color="#10B981" style={{ marginBottom: 12, opacity: 0.8 }} />
        <Typography variant="h6" sx={{ fontWeight: 800 }}>
          No Vulnerabilities Detected
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 400, mx: 'auto', mt: 0.5 }}>
          Run AI Multi-Agent Audit from the Overview tab to perform comprehensive security vulnerability and secret scanning.
        </Typography>
      </Box>
    );
  }

  const criticalCount = findings.filter((f) => f.severity === 'CRITICAL').length;
  const highCount = findings.filter((f) => f.severity === 'HIGH').length;

  return (
    <Stack spacing={3}>
      <Paper sx={{ p: 2.5, borderRadius: 3, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <ShieldAlert size={24} color="#EF4444" />
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.2 }}>
                Security Vulnerability & OWASP Audit
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Detected {findings.length} findings across code annotations, headers, and SQL/auth paths.
              </Typography>
            </Box>
          </Box>
          <Stack direction="row" spacing={1}>
            {criticalCount > 0 && <Chip label={`${criticalCount} Critical`} color="error" sx={{ fontWeight: 800 }} />}
            {highCount > 0 && <Chip label={`${highCount} High`} color="warning" sx={{ fontWeight: 800 }} />}
          </Stack>
        </Box>
      </Paper>

      <Card sx={{ p: 2.5 }}>
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Severity</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Vulnerability Description</TableCell>
                <TableCell>AI Recommendation</TableCell>
                <TableCell>Location</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {findings.map((f) => (
                <TableRow key={f.id} hover>
                  <TableCell>
                    <SeverityChip severity={f.severity} />
                  </TableCell>
                  <TableCell sx={{ fontWeight: 700, fontSize: '0.82rem' }}>
                    {f.category.replaceAll('_', ' ')}
                  </TableCell>
                  <TableCell sx={{ maxWidth: 280 }}>
                    <Typography variant="body2" sx={{ lineHeight: 1.5 }}>
                      {f.description}
                    </Typography>
                  </TableCell>
                  <TableCell sx={{ maxWidth: 280 }}>
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                      <AlertTriangle size={14} color="#F59E0B" style={{ marginTop: 2, flexShrink: 0 }} />
                      <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.4 }}>
                        {f.recommendation}
                      </Typography>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <code>{f.location}</code>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      </Card>
    </Stack>
  );
}
