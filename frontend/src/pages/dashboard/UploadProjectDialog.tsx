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
  TextField,
  Typography,
} from '@mui/material';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import { uploadProject } from '../../api/projectApi';
import { uploadFileInChunks, UploadCancelledError, type ChunkUploadProgress } from '../../api/chunkedUpload';
import { extractErrorMessage } from '../../api/httpClient';
import type { ProjectResponse } from '../../types/project';

// Small archives go through the simpler single-shot endpoint (one request, immediate result).
// Anything larger switches to the resumable chunked-upload pipeline (see api/chunkedUpload.ts),
// which streams the file up in small pieces with real progress and per-chunk retry instead of
// one giant request that a flaky connection would force restarting from scratch.
const SMALL_FILE_THRESHOLD_BYTES = 20 * 1024 * 1024;

// Sanity ceiling matching the backend's app.upload.max-total-size-bytes default (see
// backend/src/main/resources/application.yml) so an obviously-too-large file is rejected
// immediately with a clear message instead of after initiating an upload session.
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
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [progress, setProgress] = useState<ChunkUploadProgress | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cancelledRef = useRef(false);

  function resetAndClose() {
    setName('');
    setDescription('');
    setFile(null);
    setError(null);
    setProgress(null);
    cancelledRef.current = false;
    onClose();
  }

  function handleFileChange(selected: File | null) {
    if (selected && selected.size > MAX_UPLOAD_SIZE_BYTES) {
      setError(
        `"${selected.name}" is ${formatMegabytes(selected.size)}, which exceeds the ${formatMegabytes(
          MAX_UPLOAD_SIZE_BYTES,
        )} upload limit. Please remove build artifacts/dependencies (e.g. node_modules, .dart_tool, build/) and re-zip just the source code.`,
      );
      setFile(null);
      return;
    }
    setError(null);
    setFile(selected);
  }

  async function handleSubmit() {
    if (!file) {
      setError('Please choose a .zip file of your project to upload.');
      return;
    }
    setError(null);
    setIsSubmitting(true);
    cancelledRef.current = false;
    try {
      const project =
        file.size <= SMALL_FILE_THRESHOLD_BYTES
          ? await uploadProject(name, description, file)
          : await uploadFileInChunks(file, name, description, setProgress, () => cancelledRef.current);
      onUploaded(project);
      resetAndClose();
    } catch (err) {
      if (err instanceof UploadCancelledError) {
        setError('Upload cancelled.');
      } else {
        setError(extractErrorMessage(err, 'Upload failed. Please check the file and try again.'));
      }
    } finally {
      setIsSubmitting(false);
      setProgress(null);
    }
  }

  function handleCancel() {
    if (isSubmitting) {
      // Large uploads run through the chunked pipeline and poll this flag between chunks; small
      // (single-shot) uploads can't be aborted mid-flight, so Cancel just closes the dialog for them.
      cancelledRef.current = true;
      return;
    }
    resetAndClose();
  }

  return (
    <Dialog open={open} onClose={isSubmitting ? undefined : resetAndClose} fullWidth maxWidth="sm">
      <DialogTitle>Upload a project</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Upload a .zip archive of your project source code. Large projects (over{' '}
            {formatMegabytes(SMALL_FILE_THRESHOLD_BYTES)}) are uploaded in small resumable chunks
            automatically. AI TestPilot will detect the language, dependencies and API endpoints,
            then generate tests, security findings and a risk score.
          </Typography>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="Project name"
            fullWidth
            placeholder="e.g. Food Ordering Service"
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={isSubmitting}
          />
          <TextField
            label="Description (optional)"
            fullWidth
            multiline
            minRows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            disabled={isSubmitting}
          />
          <Box>
            <input
              ref={fileInputRef}
              type="file"
              accept=".zip"
              hidden
              onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
            />
            <Button
              variant="outlined"
              startIcon={<UploadFileIcon />}
              onClick={() => fileInputRef.current?.click()}
              disabled={isSubmitting}
            >
              {file ? file.name : 'Choose .zip file'}
            </Button>
          </Box>
          {progress && (
            <Box>
              <LinearProgress variant="determinate" value={progress.percent} />
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                Uploading chunk {progress.sentChunks} of {progress.totalChunks} ({progress.percent}%)
              </Typography>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={handleCancel}>{isSubmitting ? 'Cancel upload' : 'Cancel'}</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={isSubmitting}>
          {isSubmitting ? 'Uploading…' : 'Upload & Analyze'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
