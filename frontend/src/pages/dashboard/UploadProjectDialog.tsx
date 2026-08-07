import { useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  LinearProgress,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import {
  UploadCloud,
  FileArchive,
  GitBranch,
  FileCode2,
  Globe,
  Gauge,
  Sparkles,
} from 'lucide-react';
import { uploadProject, createProjectFromUrl } from '../../api/projectApi';
import { uploadFileInChunks, UploadCancelledError, type ChunkUploadProgress } from '../../api/chunkedUpload';
import { useToast } from '../../context/ToastContext';
import { extractErrorMessage } from '../../api/httpClient';
import type { ProjectResponse, ProjectSourceType } from '../../types/project';

const SMALL_FILE_THRESHOLD_BYTES = 20 * 1024 * 1024;
const MAX_UPLOAD_SIZE_BYTES = 2 * 1024 * 1024 * 1024;

function formatMegabytes(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

interface UploadProjectDialogProps {
  open: boolean;
  onClose: () => void;
  onUploaded: (project: ProjectResponse) => void;
}

export function UploadProjectDialog({ open, onClose, onUploaded }: UploadProjectDialogProps) {
  const { showSuccess } = useToast();
  const [tab, setTab] = useState<number>(0);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [repoUrl, setRepoUrl] = useState('');
  const [targetUrl, setTargetUrl] = useState('');
  const [targetApiUrl, setTargetApiUrl] = useState('');
  
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [progress, setProgress] = useState<ChunkUploadProgress | null>(null);
  
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cancelledRef = useRef(false);

  function resetAndClose() {
    setName('');
    setDescription('');
    setFile(null);
    setRepoUrl('');
    setTargetUrl('');
    setTargetApiUrl('');
    setError(null);
    setProgress(null);
    cancelledRef.current = false;
    onClose();
  }

  function handleFileChange(selected: File | null) {
    if (selected && selected.size > MAX_UPLOAD_SIZE_BYTES) {
      setError(
        `"${selected.name}" is ${formatMegabytes(selected.size)}, which exceeds the ${formatMegabytes(
          MAX_UPLOAD_SIZE_BYTES
        )} upload limit.`
      );
      setFile(null);
      return;
    }
    setError(null);
    setFile(selected);
  }

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFileChange(e.dataTransfer.files[0]);
    }
  };

  async function handleSubmit() {
    setError(null);
    setIsSubmitting(true);
    cancelledRef.current = false;

    try {
      let project: ProjectResponse;

      if (tab === 0) {
        // ZIP Upload
        if (!file) {
          setError('Please choose a .zip file to upload.');
          setIsSubmitting(false);
          return;
        }
        project = file.size <= SMALL_FILE_THRESHOLD_BYTES
          ? await uploadProject(name, description, file)
          : await uploadFileInChunks(file, name, description, setProgress, () => cancelledRef.current);
      } else if (tab === 1) {
        // Git Repo
        if (!repoUrl) {
          setError('Please enter a Git repository URL (e.g., https://github.com/org/repo)');
          setIsSubmitting(false);
          return;
        }
        project = await createProjectFromUrl({
          name: name || repoUrl.split('/').pop()?.replace('.git', '') || 'Git Repository',
          description,
          sourceType: 'GIT_URL' as ProjectSourceType,
          repoUrl,
        });
      } else if (tab === 2) {
        // OpenAPI / Postman Spec URL
        if (!targetApiUrl) {
          setError('Please enter an OpenAPI or Postman spec URL');
          setIsSubmitting(false);
          return;
        }
        project = await createProjectFromUrl({
          name: name || 'API Specification',
          description,
          sourceType: 'OPENAPI' as ProjectSourceType,
          targetApiUrl,
        });
      } else if (tab === 3) {
        // Synthetic Website URL
        if (!targetUrl) {
          setError('Please enter a website URL (e.g. https://example.com)');
          setIsSubmitting(false);
          return;
        }
        project = await createProjectFromUrl({
          name: name || targetUrl.replace(/^https?:\/\//, ''),
          description,
          sourceType: 'WEBSITE_URL' as ProjectSourceType,
          targetUrl,
        });
      } else {
        // Running API Base URL
        if (!targetApiUrl) {
          setError('Please enter an API Base URL (e.g. https://api.example.com)');
          setIsSubmitting(false);
          return;
        }
        project = await createProjectFromUrl({
          name: name || 'Target API Service',
          description,
          sourceType: 'API_URL' as ProjectSourceType,
          targetApiUrl,
        });
      }

      showSuccess(`Project "${project.name}" created successfully!`);
      onUploaded(project);
      resetAndClose();
    } catch (err) {
      if (err instanceof UploadCancelledError) {
        setError('Upload cancelled.');
      } else {
        setError(extractErrorMessage(err, 'Could not create project. Please verify inputs.'));
      }
    } finally {
      setIsSubmitting(false);
      setProgress(null);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={isSubmitting ? undefined : resetAndClose}
      fullWidth
      maxWidth="md"
      slotProps={{
        paper: {
          sx: {
            borderRadius: 3,
            backgroundColor: 'background.paper',
            backgroundImage: 'none',
            border: '1px solid',
            borderColor: 'divider',
            boxShadow: '0 25px 60px rgba(0, 0, 0, 0.6)',
          },
        },
      }}
    >
      <DialogTitle sx={{ pb: 1, pt: 3, px: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 36,
              height: 36,
              borderRadius: '10px',
              bgcolor: 'primary.main',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <UploadCloud size={20} color="#FFF" />
          </Box>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.2 }}>
              Import Project or App Target
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Upload source code, Git repositories, API specs, or live URLs for AI analysis.
            </Typography>
          </Box>
        </Box>
      </DialogTitle>

      <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 3 }}>
        <Tabs
          value={tab}
          onChange={(_e, v) => {
            setTab(v);
            setError(null);
          }}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab icon={<FileArchive size={16} />} iconPosition="start" label="ZIP Archive" />
          <Tab icon={<GitBranch size={16} />} iconPosition="start" label="Git Repository" />
          <Tab icon={<FileCode2 size={16} />} iconPosition="start" label="OpenAPI / Swagger" />
          <Tab icon={<Globe size={16} />} iconPosition="start" label="Synthetic Website" />
          <Tab icon={<Gauge size={16} />} iconPosition="start" label="Running API Target" />
        </Tabs>
      </Box>

      <DialogContent sx={{ p: 3 }}>
        <Stack spacing={2.5}>
          {error && <Alert severity="error" sx={{ borderRadius: 2 }}>{error}</Alert>}

          {/* Common Name & Description */}
          <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
            <TextField
              label="Project Name"
              fullWidth
              placeholder="e.g. Payment Gateway Service"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isSubmitting}
            />
            <TextField
              label="Description (optional)"
              fullWidth
              placeholder="e.g. Core Java Spring Boot backend"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={isSubmitting}
            />
          </Box>

          {/* TAB 0: Drag & Drop ZIP */}
          {tab === 0 && (
            <Box
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              sx={{
                p: 4,
                borderRadius: 3,
                border: '2px dashed',
                borderColor: isDragging ? 'primary.main' : 'divider',
                bgcolor: isDragging ? 'action.hover' : 'action.hover',
                textAlign: 'center',
                cursor: 'pointer',
                transition: 'all 0.2s ease',
                '&:hover': {
                  borderColor: 'primary.main',
                  bgcolor: 'action.selected',
                },
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".zip"
                hidden
                onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
              />
              <UploadCloud size={42} color={file ? '#10B981' : '#6366F1'} style={{ marginBottom: 8 }} />
              {file ? (
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700, color: 'success.main' }}>
                    {file.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Size: {formatMegabytes(file.size)} • Click or drop another file to replace
                  </Typography>
                </Box>
              ) : (
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                    Click or drag & drop your project .ZIP archive
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Automatic chunking for files over {formatMegabytes(SMALL_FILE_THRESHOLD_BYTES)}. Max file limit 2 GB.
                  </Typography>
                </Box>
              )}
            </Box>
          )}

          {/* TAB 1: Git Repository */}
          {tab === 1 && (
            <Stack spacing={2}>
              <TextField
                label="Git Repository URL"
                fullWidth
                placeholder="https://github.com/company/project-repo.git"
                value={repoUrl}
                onChange={(e) => setRepoUrl(e.target.value)}
                disabled={isSubmitting}
              />
              <Chip
                icon={<Sparkles size={14} />}
                label="Supports GitHub, GitLab, and Bitbucket public & private repos"
                variant="outlined"
                size="small"
                sx={{ width: 'fit-content' }}
              />
            </Stack>
          )}

          {/* TAB 2: OpenAPI / Postman */}
          {tab === 2 && (
            <Stack spacing={2}>
              <TextField
                label="OpenAPI / Swagger Spec URL"
                fullWidth
                placeholder="https://petstore.swagger.io/v2/swagger.json"
                value={targetApiUrl}
                onChange={(e) => setTargetApiUrl(e.target.value)}
                disabled={isSubmitting}
              />
              <Typography variant="caption" color="text.secondary">
                QPilot will parse endpoints, data schemas, authentication headers, and auto-generate test suites.
              </Typography>
            </Stack>
          )}

          {/* TAB 3: Synthetic Website URL */}
          {tab === 3 && (
            <Stack spacing={2}>
              <TextField
                label="Website URL"
                fullWidth
                placeholder="https://example.com"
                value={targetUrl}
                onChange={(e) => setTargetUrl(e.target.value)}
                disabled={isSubmitting}
              />
              <Typography variant="caption" color="text.secondary">
                Enables Synthetic Auditor to crawl links, test mobile responsiveness, audit accessibility (WCAG), and detect technology stack.
              </Typography>
            </Stack>
          )}

          {/* TAB 4: Running API Target */}
          {tab === 4 && (
            <Stack spacing={2}>
              <TextField
                label="Target API Base URL"
                fullWidth
                placeholder="https://api.example.com/v1"
                value={targetApiUrl}
                onChange={(e) => setTargetApiUrl(e.target.value)}
                disabled={isSubmitting}
              />
              <Typography variant="caption" color="text.secondary">
                Used for automated endpoint discovery, rate limit probing, and safe performance load stress testing.
              </Typography>
            </Stack>
          )}

          {/* Progress Bar for Chunked Upload */}
          {progress && (
            <Box sx={{ mt: 1 }}>
              <LinearProgress variant="determinate" value={progress.percent} sx={{ height: 8, borderRadius: 4 }} />
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.8 }}>
                <Typography variant="caption" color="text.secondary">
                  Sending chunk {progress.sentChunks} of {progress.totalChunks}
                </Typography>
                <Typography variant="caption" color="primary.main" sx={{ fontWeight: 700 }}>
                  {progress.percent}%
                </Typography>
              </Box>
            </Box>
          )}
        </Stack>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={resetAndClose} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button variant="contained" onClick={handleSubmit} disabled={isSubmitting} sx={{ px: 3, fontWeight: 700 }}>
          {isSubmitting ? 'Importing…' : 'Import & Analyze'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
