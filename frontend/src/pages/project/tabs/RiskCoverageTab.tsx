import { Card, CardContent, Chip, Grid, List, ListItem, ListItemIcon, ListItemText, Stack, Typography } from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { RiskAssessmentResponse } from '../../../types/analysis';

function riskColor(score: number): string {
  if (score >= 70) return '#c62828';
  if (score >= 40) return '#e69120';
  return '#2e7d32';
}

function riskLabel(score: number): string {
  if (score >= 70) return 'High Risk';
  if (score >= 40) return 'Medium Risk';
  return 'Low Risk';
}

export function RiskCoverageTab({ risk }: { risk?: RiskAssessmentResponse }) {
  if (!risk) {
    return (
      <Typography color="text.secondary">
        No risk assessment yet. Run analysis from the Overview tab.
      </Typography>
    );
  }

  const color = riskColor(risk.score);
  const riskData = [
    { name: 'Risk', value: risk.score },
    { name: 'Remaining', value: 100 - risk.score },
  ];
  const coverageData = [
    { name: 'Estimated coverage', value: risk.coverageEstimatePercent },
    { name: 'Gap', value: 100 - risk.coverageEstimatePercent },
  ];

  return (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12, sm: 6 }}>
        <Card variant="outlined">
          <CardContent sx={{ textAlign: 'center' }}>
            <Typography variant="h6" sx={{ mb: 1 }}>Overall Risk Score</Typography>
            <div style={{ width: '100%', height: 200, position: 'relative' }}>
              <ResponsiveContainer>
                <PieChart>
                  <Pie data={riskData} dataKey="value" innerRadius={60} outerRadius={85} startAngle={90} endAngle={-270}>
                    <Cell fill={color} />
                    <Cell fill="#e0e0e0" />
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
              <div
                style={{
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  transform: 'translate(-50%, -50%)',
                  textAlign: 'center',
                }}
              >
                <Typography variant="h4" sx={{ color, fontWeight: 700 }}>{risk.score}%</Typography>
              </div>
            </div>
            <Chip label={riskLabel(risk.score)} sx={{ bgcolor: color, color: 'white', fontWeight: 600 }} />
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, sm: 6 }}>
        <Card variant="outlined">
          <CardContent sx={{ textAlign: 'center' }}>
            <Typography variant="h6" sx={{ mb: 1 }}>Estimated Test Coverage</Typography>
            <div style={{ width: '100%', height: 200, position: 'relative' }}>
              <ResponsiveContainer>
                <PieChart>
                  <Pie data={coverageData} dataKey="value" innerRadius={60} outerRadius={85} startAngle={90} endAngle={-270}>
                    <Cell fill="#1e3c72" />
                    <Cell fill="#e0e0e0" />
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
              <div
                style={{
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  transform: 'translate(-50%, -50%)',
                  textAlign: 'center',
                }}
              >
                <Typography variant="h4" sx={{ color: '#1e3c72', fontWeight: 700 }}>
                  {risk.coverageEstimatePercent}%
                </Typography>
              </div>
            </div>
            <Chip label="AI estimated" variant="outlined" />
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, sm: 6 }}>
        <Card variant="outlined">
          <CardContent>
            <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: 'center' }}>
              <WarningAmberIcon color="warning" />
              <Typography variant="h6">Why this score?</Typography>
            </Stack>
            <List dense>
              {risk.reasons.map((reason, idx) => (
                <ListItem key={idx} disableGutters>
                  <ListItemText primary={reason} />
                </ListItem>
              ))}
            </List>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, sm: 6 }}>
        <Card variant="outlined">
          <CardContent>
            <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: 'center' }}>
              <CheckCircleOutlineIcon color="error" />
              <Typography variant="h6">Coverage Gaps</Typography>
            </Stack>
            <List dense>
              {risk.coverageGaps.map((gap, idx) => (
                <ListItem key={idx} disableGutters>
                  <ListItemIcon sx={{ minWidth: 28 }}>
                    <WarningAmberIcon fontSize="small" color="warning" />
                  </ListItemIcon>
                  <ListItemText primary={gap} />
                </ListItem>
              ))}
            </List>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );
}
