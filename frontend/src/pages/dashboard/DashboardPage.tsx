import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import FolderZipIcon from '@mui/icons-material/FolderZip';
import { AppLayout } from '../../components/layout/AppLayout';
import { StatusChip } from '../../components/common/StatusChip';
import { UploadProjectDialog } from './UploadProjectDialog';
import { listProjects } from '../../api/projectApi';
import { extractErrorMessage } from '../../api/httpClient';
import type { ProjectResponse } from '../../types/project';

export function DashboardPage() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  async function loadProjects() {
    setIsLoading(true);
    setError(null);
    try {
      const data = await listProjects();
      setProjects(data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load your projects.'));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadProjects();
  }, []);

  return (
    <AppLayout>
      <Stack direction="row" spacing={2} sx={{ mb: 3, justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Typography variant="h4">Projects</Typography>
          <Typography variant="body2" color="text.secondary">
            Upload a project and let AI TestPilot generate tests, security findings and risk scores.
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
          Upload Project
        </Button>
      </Stack>

      {error && (
        <Typography color="error" sx={{ mb: 2 }}>
          {error}
        </Typography>
      )}

      {!isLoading && projects.length === 0 && !error && (
        <Card variant="outlined" sx={{ textAlign: 'center', py: 6 }}>
          <FolderZipIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
          <Typography variant="h6">No projects yet</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Upload your first project to get an AI-generated quality report.
          </Typography>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
            Upload Project
          </Button>
        </Card>
      )}

      <Grid container spacing={3}>
        {projects.map((project) => (
          <Grid key={project.id} size={{ xs: 12, sm: 6, md: 4 }}>
            <Card variant="outlined" sx={{ height: '100%' }}>
              <CardActionArea onClick={() => navigate(`/projects/${project.id}`)} sx={{ height: '100%', p: 1 }}>
                <CardContent>
                  <Stack direction="row" spacing={1} sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <Typography variant="h6" noWrap sx={{ maxWidth: 180 }}>
                      {project.name}
                    </Typography>
                    <StatusChip status={project.status} />
                  </Stack>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    {project.primaryLanguage ?? 'Unknown language'} • {project.fileCount ?? 0} files
                  </Typography>
                  {project.description && (
                    <Typography variant="body2" sx={{ mt: 1 }} color="text.secondary" noWrap>
                      {project.description}
                    </Typography>
                  )}
                  <Typography variant="caption" color="text.disabled" sx={{ mt: 1, display: 'block' }}>
                    Updated {new Date(project.updatedAt).toLocaleString()}
                  </Typography>
                </CardContent>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>

      <UploadProjectDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onUploaded={(project) => {
          setProjects((prev) => [project, ...prev]);
          navigate(`/projects/${project.id}`);
        }}
      />
    </AppLayout>
  );
}
