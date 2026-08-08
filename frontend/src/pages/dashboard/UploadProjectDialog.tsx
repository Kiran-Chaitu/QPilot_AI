import { useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
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
import { FileArchive, FileCode2, Gauge, Globe, GitBranch, Info, UploadCloud } from 'lucide-react';
import { createProjectFromUrl, uploadProject } from '../../api/projectApi';
import { uploadFileInChunks, UploadCancelledError, type ChunkUploadProgress } from '../../api/chunkedUpload';
import { useToast } from '../../context/ToastContext';
import { extractErrorMessage } from '../../api/httpClient';
import { brand, status as statusColors } from '../../theme/palette';
import type { ProjectResponse } from '../../types/project';

/** Above this size the upload switches to the resumable chunked pipeline. */
const CHUNKED_THRESHOLD_BYTES = 20 * 1024 * 1024;
const MAX_UPLOAD_SIZE_BYTES = 2 * 1024 * 1024 * 1024;

type ImportMode = 'zip' | 'openapi' | 'website' | 'api' | 'git';

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

interface UploadProjectDialogProps {
  open: boolean;
  onClose: () => void;
  onUploaded: (project: ProjectResponse) => void;
}

/**
 * Project import dialog.
 *
 * <p>Each tab describes only what QPilot genuinely does with that input. The previous version advertised
 * capabilities that did not exist — Git cloning from "GitHub, GitLab and Bitbucket public &amp; private
 * repos", plus mobile-responsiveness and full WCAG auditing — and creating a Git project silently produced
 * one with no source and no analyzable content. Git import is now explicitly marked unavailable, with the
 * workaround, rather than accepting input it cannot act on.
 */
export function UploadProjectDialog({ open, onClose, onUploaded }: UploadProjectDialogProps) {
  const { showSuccess } = useToast();
  const [mode, setMode] = useState<ImportMode>('zip');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [specUrl, setSpecUrl] = useState('');
  const [websiteUrl, setWebsiteUrl] = useState('');
  const [apiBaseUrl, setApiBaseUrl] = useState('');

  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [progress, setProgress] = useState<ChunkUploadProgress | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  // A ref rather than state: the chunked uploader polls this between chunks, and it must observe the
  // current value without waiting for a re-render.
  const cancelledRef = useRef(false);

  const reset = () => {
    setName('');
    setDescription('');
    setFile(null);
    setSpecUrl('');
    setWebsiteUrl('');
    setApiBaseUrl('');
    setError(null);
    setProgress(null);
    cancelledRef.current = false;
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  /** Cancels an in-flight chunked upload. The uploader aborts the server-side session on the next check. */
  const handleCancelUpload = () => {
    cancelledRef.current = true;
  };

  const selectFile = (selected: File | null) => {
    if (!selected) {
      setFile(null);
      return;
    }
    if (!selected.name.toLowerCase().endsWith('.zip')) {
      setError(`"${selected.name}" is not a .zip archive. QPilot only accepts zip archives for source upload.`);
      setFile(null);
      return;
    }
    if (selected.size > MAX_UPLOAD_SIZE_BYTES) {
      setError(
        `"${selected.name}" is ${formatSize(selected.size)}, over the ${formatSize(MAX_UPLOAD_SIZE_BYTES)} limit. ` +
          'Remove build output and dependency directories (node_modules, target, dist) before zipping.',
      );
      setFile(null);
      return;
    }
    setError(null);
    setFile(selected);
  };

  const canSubmit = (() => {
    if (isSubmitting) return false;
    switch (mode) {
      case 'zip':
        return file !== null;
      case 'openapi':
        return specUrl.trim().length > 0;
      case 'website':
        return websiteUrl.trim().length > 0;
      case 'api':
        return apiBaseUrl.trim().length > 0;
      case 'git':
        return false; // not implemented server-side
      default:
        return false;
    }
  })();

  const handleSubmit = async () => {
    setError(null);
    setIsSubmitting(true);
    cancelledRef.current = false;

    try {
      let project: ProjectResponse;

      if (mode === 'zip') {
        if (!file) {
          setError('Choose a .zip archive first.');
          return;
        }
        project =
          file.size <= CHUNKED_THRESHOLD_BYTES
            ? await uploadProject(name, description, file)
            : await uploadFileInChunks(file, name, description, setProgress, () => cancelledRef.current);
      } else if (mode === 'openapi') {
        project = await createProjectFromUrl({
          name: name.trim() || undefined,
          description,
          sourceType: 'OPENAPI',
          targetApiUrl: specUrl.trim(),
        });
      } else if (mode === 'website') {
        project = await createProjectFromUrl({
          name: name.trim() || undefined,
          description,
          sourceType: 'WEBSITE_URL',
          targetUrl: websiteUrl.trim(),
        });
      } else {
        project = await createProjectFromUrl({
          name: name.trim() || undefined,
          description,
          sourceType: 'API_URL',
          targetApiUrl: apiBaseUrl.trim(),
        });
      }

      showSuccess(`Project "${project.name}" created.`);
      onUploaded(project);
      reset();
      onClose();
    } catch (err) {
      if (err instanceof UploadCancelledError) {
        setError('Upload cancelled. The partially-uploaded data was discarded on the server.');
      } else {
        setError(extractErrorMessage(err, 'Could not create the project.'));
      }
    } finally {
      setIsSubmitting(false);
      setProgress(null);
    }
  };

  return (
    <Dialog open={open} onClose={isSubmitting ? undefined : handleClose} fullWidth maxWidth="md">
      <DialogTitle sx={{ pb: 1.5, pt: 3, px: 3 }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Box
            sx={{
              width: 38,
              height: 38,
              borderRadius: 2.5,
              background: `linear-gradient(135deg, ${brand.primary} 0%, ${brand.secondary} 100%)`,
              display: 'grid',
              placeItems: 'center',
              flexShrink: 0,
            }}
          >
            <UploadCloud size={20} color="#0B0C12" />
          </Box>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.2 }}>
              Add a project
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Upload source for code-level analysis, or point QPilot at a live target.
            </Typography>
          </Box>
        </Stack>
      </DialogTitle>

      <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 3 }}>
        <Tabs
          value={mode}
          onChange={(_, value) => {
            setMode(value);
            setError(null);
          }}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab value="zip" icon={<FileArchive size={15} />} iconPosition="start" label="Source archive" />
          <Tab value="openapi" icon={<FileCode2 size={15} />} iconPosition="start" label="OpenAPI spec" />
          <Tab value="website" icon={<Globe size={15} />} iconPosition="start" label="Website URL" />
          <Tab value="api" icon={<Gauge size={15} />} iconPosition="start" label="API base URL" />
          <Tab value="git" icon={<GitBranch size={15} />} iconPosition="start" label="Git repo" />
        </Tabs>
      </Box>

      <DialogContent sx={{ p: 3 }}>
        <Stack spacing={2.5}>
          {error && (
            <Alert severity="error" variant="outlined" sx={{ borderRadius: 2.5 }}>
              <Typography variant="body2">{error}</Typography>
            </Alert>
          )}

          {mode !== 'git' && (
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Project name (optional)"
                fullWidth
                placeholder="Leave blank to derive it from the source"
                value={name}
                onChange={(event) => setName(event.target.value)}
                disabled={isSubmitting}
              />
              <TextField
                label="Description (optional)"
                fullWidth
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                disabled={isSubmitting}
              />
            </Stack>
          )}

          {mode === 'zip' && (
            <>
              <Box
                onDragOver={(event) => {
                  event.preventDefault();
                  setIsDragging(true);
                }}
                onDragLeave={() => setIsDragging(false)}
                onDrop={(event) => {
                  event.preventDefault();
                  setIsDragging(false);
                  selectFile(event.dataTransfer.files?.[0] ?? null);
                }}
                onClick={() => !isSubmitting && fileInputRef.current?.click()}
                sx={{
                  p: 4,
                  borderRadius: 3,
                  border: '2px dashed',
                  borderColor: isDragging ? 'primary.main' : file ? 'success.main' : 'divider',
                  bgcolor: isDragging ? 'action.selected' : 'action.hover',
                  textAlign: 'center',
                  cursor: isSubmitting ? 'default' : 'pointer',
                  transition: 'border-color 180ms ease, background-color 180ms ease',
                  '&:hover': { borderColor: isSubmitting ? undefined : 'primary.main' },
                }}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".zip"
                  hidden
                  onChange={(event) => selectFile(event.target.files?.[0] ?? null)}
                />
                <UploadCloud size={40} color={file ? statusColors.success : brand.primary} style={{ marginBottom: 8 }} />
                {file ? (
                  <>
                    <Typography variant="subtitle1" sx={{ fontWeight: 750, color: 'success.main', overflowWrap: 'anywhere' }}>
                      {file.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {formatSize(file.size)} · click to choose a different archive
                    </Typography>
                  </>
                ) : (
                  <>
                    <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                      Drop a .zip archive here, or click to browse
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Archives over {formatSize(CHUNKED_THRESHOLD_BYTES)} upload in resumable chunks. Limit{' '}
                      {formatSize(MAX_UPLOAD_SIZE_BYTES)}.
                    </Typography>
                  </>
                )}
              </Box>
              <Alert severity="info" variant="outlined" icon={<Info size={17} />} sx={{ borderRadius: 2.5 }}>
                <Typography variant="caption" color="text.secondary">
                  Build output and dependency directories (node_modules, target, dist, .git) are skipped during
                  extraction, so you can zip the project as-is — they simply will not count toward the size limit or
                  the analysis.
                </Typography>
              </Alert>
            </>
          )}

          {mode === 'openapi' && (
            <Stack spacing={1.5}>
              <TextField
                label="OpenAPI / Swagger document URL"
                fullWidth
                placeholder="https://api.example.com/v3/api-docs"
                value={specUrl}
                onChange={(event) => setSpecUrl(event.target.value)}
                disabled={isSubmitting}
              />
              <Typography variant="caption" color="text.secondary">
                QPilot fetches the document and parses its real paths, methods, parameters and response codes, then
                generates an API test per route. Source-level metrics (file counts, code scanning) need the source
                archive instead.
              </Typography>
            </Stack>
          )}

          {mode === 'website' && (
            <Stack spacing={1.5}>
              <TextField
                label="Website URL"
                fullWidth
                placeholder="https://your-site.example.com"
                value={websiteUrl}
                onChange={(event) => setWebsiteUrl(event.target.value)}
                disabled={isSubmitting}
              />
              <Typography variant="caption" color="text.secondary">
                Enables the live audit: response timings, TLS certificate, redirect chain, security headers, cookie
                flags, link checking, and the SEO and accessibility properties detectable from the returned HTML.
                Rendered-page metrics and full WCAG conformance are not covered — those need a real browser.
              </Typography>
            </Stack>
          )}

          {mode === 'api' && (
            <Stack spacing={1.5}>
              <TextField
                label="API base URL"
                fullWidth
                placeholder="https://staging-api.example.com"
                value={apiBaseUrl}
                onChange={(event) => setApiBaseUrl(event.target.value)}
                disabled={isSubmitting}
              />
              <Typography variant="caption" color="text.secondary">
                QPilot probes the conventional OpenAPI locations under this URL. If it finds a document, it uses those
                real endpoints; if not, it says so rather than inventing any. This target is also used for load testing
                and rate-limit probing.
              </Typography>
            </Stack>
          )}

          {mode === 'git' && (
            <Alert severity="warning" variant="outlined" icon={<Info size={18} />} sx={{ borderRadius: 3 }}>
              <Typography variant="body2" sx={{ fontWeight: 750, mb: 0.75 }}>
                Git import is not available
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                QPilot does not clone repositories — there is no server-side git integration or credential handling, so
                accepting a repository URL here would create a project with no source to analyze.
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                <strong>Instead:</strong> download the repository as a zip archive (on GitHub: Code → Download ZIP, or{' '}
                <Box component="code" sx={{ fontSize: '0.72rem' }}>
                  git archive -o project.zip HEAD
                </Box>
                ) and upload it under <strong>Source archive</strong>. That gives full code-level analysis.
              </Typography>
              <Button size="small" variant="outlined" onClick={() => setMode('zip')} sx={{ mt: 1.5, fontWeight: 700 }}>
                Switch to source archive
              </Button>
            </Alert>
          )}

          {progress && (
            <Box>
              <LinearProgress variant="determinate" value={progress.percent} sx={{ height: 8 }} />
              <Stack direction="row" sx={{ justifyContent: 'space-between', mt: 0.75 }}>
                <Typography variant="caption" color="text.secondary">
                  Uploading chunk {progress.sentChunks} of {progress.totalChunks}
                  {cancelledRef.current ? ' — cancelling…' : ''}
                </Typography>
                <Typography variant="caption" color="primary.light" sx={{ fontWeight: 750 }}>
                  {progress.percent}%
                </Typography>
              </Stack>
            </Box>
          )}
        </Stack>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 3 }}>
        {/*
          During a chunked upload the button becomes a working cancel control. Previously Cancel was
          disabled while submitting, so a large upload could not be stopped at all once started.
        */}
        {progress ? (
          <Button onClick={handleCancelUpload} color="error" disabled={cancelledRef.current} sx={{ fontWeight: 700 }}>
            Cancel upload
          </Button>
        ) : (
          <Button onClick={handleClose} disabled={isSubmitting} sx={{ fontWeight: 700 }}>
            Close
          </Button>
        )}
        <Button variant="contained" onClick={handleSubmit} disabled={!canSubmit} sx={{ px: 3, fontWeight: 780 }}>
          {isSubmitting ? 'Importing…' : 'Add project'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
