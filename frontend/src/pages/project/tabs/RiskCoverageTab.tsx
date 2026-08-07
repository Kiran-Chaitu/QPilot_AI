import { Box, Card, CardContent, Chip, Grid, List, ListItem, ListItemIcon, ListItemText, Stack, Typography } from '@mui/material';
import { AlertTriangle, ShieldAlert, Sparkles } from 'lucide-react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { RiskAssessmentResponse } from '../../../types/analysis';

function riskColor(score: number): string {
  if (score >= 70) return '#EF4444';
  if (score >= 40) return '#F59E0B';
  return '#10B981';
}

function riskLabel(score: number): string {
  if (score >= 70) return 'High Risk Index';
  if (score >= 40) return 'Moderate Risk Profile';
  return 'Low Vulnerability Index';
}

export function RiskCoverageTab({ risk }: { risk?: RiskAssessmentResponse }) {
  if (!risk) {
    return (
      <Box sx={{ textAlign: 'center', py: 8 }}>
        <ShieldAlert size={48} color="#10B981" style={{ marginBottom: 12, opacity: 0.8 }} />
        <Typography variant="h6" sx={{ fontWeight: 800 }}>
          No Risk & Coverage Model Generated
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 400, mx: 'auto', mt: 0.5 }}>
          Run AI Multi-Agent Audit from the Overview tab to calculate repository risk indices and surface test coverage gaps.
        </Typography>
      </Box>
    );
  }

  const color = riskColor(risk.score);
  const riskData = [
    { name: 'Risk Score', value: risk.score },
    { name: 'Safe Profile', value: 100 - risk.score },
  ];
  const coverageData = [
    { name: 'Estimated Coverage', value: risk.coverageEstimatePercent },
    { name: 'Coverage Gap', value: 100 - risk.coverageEstimatePercent },
  ];

  return (
    <Grid container spacing={3}>
      {/* Risk Score Gauge */}
      <Grid size={{ xs: 12, sm: 6 }}>
        <Card sx={{ p: 2.5, textAlign: 'center', height: '100%' }}>
          <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
            Calculated System Risk Index
          </Typography>
          <Box sx={{ width: '100%', height: 210, position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie data={riskData} dataKey="value" innerRadius={65} outerRadius={88} startAngle={90} endAngle={-270} paddingAngle={2}>
                  <Cell fill={color} />
                  <Cell fill="rgba(255,255,255,0.08)" />
                </Pie>
                <Tooltip contentStyle={{ backgroundColor: '#121215', borderRadius: 8, color: '#FAFAFA' }} />
              </PieChart>
            </ResponsiveContainer>
            <Box
              sx={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                textAlign: 'center',
              }}
            >
              <Typography variant="h3" sx={{ color, fontWeight: 800, lineHeight: 1 }}>
                {risk.score}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                OUT OF 100
              </Typography>
            </Box>
          </Box>
          <Chip label={riskLabel(risk.score)} sx={{ bgcolor: `${color}22`, color, fontWeight: 800, border: `1px solid ${color}44` }} />
        </Card>
      </Grid>

      {/* Coverage Gauge */}
      <Grid size={{ xs: 12, sm: 6 }}>
        <Card sx={{ p: 2.5, textAlign: 'center', height: '100%' }}>
          <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
            AI Code Test Coverage Estimate
          </Typography>
          <Box sx={{ width: '100%', height: 210, position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie data={coverageData} dataKey="value" innerRadius={65} outerRadius={88} startAngle={90} endAngle={-270} paddingAngle={2}>
                  <Cell fill="#10B981" />
                  <Cell fill="rgba(255,255,255,0.08)" />
                </Pie>
                <Tooltip contentStyle={{ backgroundColor: '#121215', borderRadius: 8, color: '#FAFAFA' }} />
              </PieChart>
            </ResponsiveContainer>
            <Box
              sx={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                textAlign: 'center',
              }}
            >
              <Typography variant="h3" sx={{ color: '#10B981', fontWeight: 800, lineHeight: 1 }}>
                {risk.coverageEstimatePercent}%
              </Typography>
              <Typography variant="caption" color="text.secondary">
                ESTIMATED COVERAGE
              </Typography>
            </Box>
          </Box>
          <Chip icon={<Sparkles size={14} color="#10B981" />} label="RAG Semantic Analysis" variant="outlined" color="primary" sx={{ fontWeight: 700 }} />
        </Card>
      </Grid>

      {/* Risk Reasons */}
      <Grid size={{ xs: 12, sm: 6 }}>
        <Card sx={{ p: 2.5, height: '100%' }}>
          <CardContent sx={{ p: 0 }}>
            <Stack direction="row" spacing={1} sx={{ mb: 2, alignItems: 'center' }}>
              <AlertTriangle color="#F59E0B" size={20} />
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                Risk Factors & Drivers
              </Typography>
            </Stack>
            <List dense disablePadding>
              {risk.reasons.map((reason, idx) => (
                <ListItem key={idx} disableGutters sx={{ py: 0.8 }}>
                  <ListItemIcon sx={{ minWidth: 26 }}>
                    <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: '#F59E0B' }} />
                  </ListItemIcon>
                  <ListItemText primary={<Typography variant="body2" sx={{ fontWeight: 600 }}>{reason}</Typography>} />
                </ListItem>
              ))}
            </List>
          </CardContent>
        </Card>
      </Grid>

      {/* Coverage Gaps */}
      <Grid size={{ xs: 12, sm: 6 }}>
        <Card sx={{ p: 2.5, height: '100%' }}>
          <CardContent sx={{ p: 0 }}>
            <Stack direction="row" spacing={1} sx={{ mb: 2, alignItems: 'center' }}>
              <ShieldAlert color="#EF4444" size={20} />
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                Uncovered Vulnerability & Gap Map
              </Typography>
            </Stack>
            <List dense disablePadding>
              {risk.coverageGaps.map((gap, idx) => (
                <ListItem key={idx} disableGutters sx={{ py: 0.8 }}>
                  <ListItemIcon sx={{ minWidth: 26 }}>
                    <AlertTriangle size={16} color="#EF4444" />
                  </ListItemIcon>
                  <ListItemText primary={<Typography variant="body2" color="text.secondary">{gap}</Typography>} />
                </ListItem>
              ))}
            </List>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );
}
