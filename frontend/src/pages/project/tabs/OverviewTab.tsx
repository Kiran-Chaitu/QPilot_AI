import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { StatusChip } from '../../../components/common/StatusChip';
import type { ProjectResponse, ProjectStructureSummary } from '../../../types/project';
import type { AnalysisResultResponse } from '../../../types/analysis';

interface OverviewTabProps {
  // Structure is guaranteed non-null here: ProjectDetailPage only renders this tab once
  // background extraction/analysis has produced a structure summary (see its EXTRACTING guard).
  detail: { project: ProjectResponse; structure: ProjectStructureSummary };
  analysis: AnalysisResultResponse | null;
  onAnalyze: () => void;
  isAnalyzing: boolean;
}

export function OverviewTab({ detail, analysis, onAnalyze, isAnalyzing }: OverviewTabProps) {
  const { project, structure } = detail;

  return (
    <Grid container spacing={3}>
      <Grid size={{ xs: 12, md: 7 }}>
        <Card variant="outlined">
          <CardContent>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <StatusChip status={project.status} />
            </Stack>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid size={4}>
                <Typography variant="caption" color="text.secondary">Primary language</Typography>
                <Typography variant="body1">{structure.primaryLanguage ?? 'Unknown'}</Typography>
              </Grid>
              <Grid size={4}>
                <Typography variant="caption" color="text.secondary">Total files</Typography>
                <Typography variant="body1">{structure.totalFiles}</Typography>
              </Grid>
              <Grid size={4}>
                <Typography variant="caption" color="text.secondary">Endpoints detected</Typography>
                <Typography variant="body1">{structure.endpoints.length}</Typography>
              </Grid>
            </Grid>

            {Object.keys(structure.languageBreakdown).length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="caption" color="text.secondary">Language breakdown</Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap', gap: 1 }}>
                  {Object.entries(structure.languageBreakdown).map(([lang, count]) => (
                    <Chip key={lang} size="small" label={`${lang}: ${count}`} />
                  ))}
                </Stack>
              </Box>
            )}

            {structure.dependencies.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="caption" color="text.secondary">Dependencies</Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap', gap: 1 }}>
                  {structure.dependencies.slice(0, 12).map((dep) => (
                    <Chip key={dep} size="small" variant="outlined" label={dep} />
                  ))}
                </Stack>
              </Box>
            )}

            {structure.endpoints.length > 0 && (
              <Box sx={{ mt: 3 }}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>Detected API Endpoints</Typography>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Method</TableCell>
                      <TableCell>Path</TableCell>
                      <TableCell>Source file</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {structure.endpoints.map((ep, idx) => (
                      <TableRow key={idx}>
                        <TableCell><Chip size="small" label={ep.httpMethod} /></TableCell>
                        <TableCell><code>{ep.path}</code></TableCell>
                        <TableCell sx={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {ep.sourceFile}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 5 }}>
        <Card variant="outlined" sx={{ mb: 3 }}>
          <CardContent>
            <Stack direction="row" sx={{ mb: 1, justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="h6">AI Analysis</Typography>
              <Button
                variant="contained"
                size="small"
                startIcon={<PlayArrowIcon />}
                onClick={onAnalyze}
                disabled={isAnalyzing}
              >
                {isAnalyzing ? 'Analyzing…' : analysis ? 'Re-analyze' : 'Analyze'}
              </Button>
            </Stack>
            {!analysis && (
              <Typography variant="body2" color="text.secondary">
                Run AI analysis to get a code summary, generated tests, security findings and a risk score.
              </Typography>
            )}
            {analysis && (
              <Box>
                <Typography variant="body2" sx={{ mb: 1.5 }}>{analysis.run.codeSummary}</Typography>
                {analysis.run.keyResponsibilities.length > 0 && (
                  <List dense>
                    {analysis.run.keyResponsibilities.map((item, idx) => (
                      <ListItem key={idx} disableGutters>
                        <ListItemIcon sx={{ minWidth: 28 }}>
                          <CheckCircleIcon fontSize="small" color="success" />
                        </ListItemIcon>
                        <ListItemText primary={item} />
                      </ListItem>
                    ))}
                  </List>
                )}
                {analysis.run.notableObservations.length > 0 && (
                  <List dense>
                    {analysis.run.notableObservations.map((item, idx) => (
                      <ListItem key={idx} disableGutters>
                        <ListItemIcon sx={{ minWidth: 28 }}>
                          <InfoOutlinedIcon fontSize="small" color="info" />
                        </ListItemIcon>
                        <ListItemText primary={item} secondary={undefined} />
                      </ListItem>
                    ))}
                  </List>
                )}
                <Typography variant="caption" color="text.disabled">
                  AI provider: {analysis.run.aiProvider}
                </Typography>
              </Box>
            )}
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );
}
