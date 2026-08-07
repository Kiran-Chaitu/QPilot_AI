import {
  Card,
  CardContent,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import ShieldIcon from '@mui/icons-material/Shield';
import { SeverityChip } from '../../../components/common/SeverityChip';
import type { SecurityFindingResponse } from '../../../types/analysis';

export function SecurityReportTab({ findings }: { findings: SecurityFindingResponse[] }) {
  if (findings.length === 0) {
    return (
      <Typography color="text.secondary">
        No security findings yet. Run analysis from the Overview tab.
      </Typography>
    );
  }

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" spacing={1} sx={{ mb: 2, alignItems: 'center' }}>
          <ShieldIcon color="primary" />
          <Typography variant="h6">Security Findings ({findings.length})</Typography>
        </Stack>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Severity</TableCell>
              <TableCell>Category</TableCell>
              <TableCell>Description</TableCell>
              <TableCell>Recommendation</TableCell>
              <TableCell>Location</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {findings.map((f) => (
              <TableRow key={f.id}>
                <TableCell><SeverityChip severity={f.severity} /></TableCell>
                <TableCell>{f.category.replaceAll('_', ' ')}</TableCell>
                <TableCell sx={{ maxWidth: 260 }}>{f.description}</TableCell>
                <TableCell sx={{ maxWidth: 260 }}>{f.recommendation}</TableCell>
                <TableCell><code>{f.location}</code></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
