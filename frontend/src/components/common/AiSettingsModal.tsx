import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Sparkles, Key, CheckCircle, RefreshCw, X, ShieldAlert, Cpu } from 'lucide-react';
import { getAiConfig, updateAiConfig, type AiConfig } from '../../api/aiApi';
import { useToast } from '../../context/ToastContext';

interface AiSettingsModalProps {
  open: boolean;
  onClose: () => void;
  onConfigChanged?: () => void;
}

export function AiSettingsModal({ open, onClose, onConfigChanged }: AiSettingsModalProps) {
  const { showSuccess, showError } = useToast();
  const [config, setConfig] = useState<AiConfig | null>(null);
  const [apiKey, setApiKey] = useState('');
  const [model, setModel] = useState('gemini-2.0-flash');
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (open) {
      loadConfig();
    }
  }, [open]);

  async function loadConfig() {
    setIsLoading(true);
    try {
      const data = await getAiConfig();
      setConfig(data);
      if (data.model) setModel(data.model);
    } catch {
      // silently ignore on load failure — user may not be authenticated yet
    } finally {
      setIsLoading(false);
    }
  }

  async function handleSave() {
    setIsSaving(true);
    try {
      const updated = await updateAiConfig(apiKey, model);
      setConfig(updated);
      setApiKey('');
      showSuccess(updated.statusMessage || 'AI configuration updated successfully!');
      if (onConfigChanged) onConfigChanged();
      // Auto-close dialog after successful save
      onClose();
    } catch {
      showError('Failed to update Gemini API key configuration.');
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      slotProps={{
        paper: {
          sx: {
            borderRadius: 4,
            background: 'linear-gradient(145deg, rgba(15, 23, 42, 0.95) 0%, rgba(9, 13, 22, 0.98) 100%)',
            backdropFilter: 'blur(20px)',
            border: '1px solid rgba(255, 255, 255, 0.12)',
            boxShadow: '0 20px 60px rgba(0, 0, 0, 0.6)',
          },
        },
      }}
    >
      <DialogTitle sx={{ m: 0, p: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Box
            sx={{
              p: 1,
              borderRadius: 2,
              background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(168, 85, 247, 0.2) 100%)',
              border: '1px solid rgba(99, 102, 241, 0.4)',
            }}
          >
            <Sparkles size={22} color="#818CF8" />
          </Box>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              AI Engine & Gemini API Key Settings
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Configure Gemini AI model or use Smart Offline Engine
            </Typography>
          </Box>
        </Stack>
        <IconButton onClick={onClose} size="small" sx={{ color: 'text.secondary' }}>
          <X size={18} />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers sx={{ p: 3, borderColor: 'rgba(255, 255, 255, 0.08)' }}>
        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={32} />
          </Box>
        ) : (
          <Stack spacing={3}>
            {/* Status Banner */}
            <Box
              sx={{
                p: 2.5,
                borderRadius: 3,
                border: '1px solid',
                borderColor: config?.provider === 'gemini' ? 'success.main' : 'warning.main',
                background:
                  config?.provider === 'gemini'
                    ? 'linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(6, 182, 212, 0.05) 100%)'
                    : 'linear-gradient(135deg, rgba(245, 158, 11, 0.1) 0%, rgba(239, 68, 68, 0.05) 100%)',
              }}
            >
              <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
                {config?.provider === 'gemini' ? (
                  <CheckCircle size={22} color="#10B981" style={{ marginTop: 2 }} />
                ) : (
                  <ShieldAlert size={22} color="#F59E0B" style={{ marginTop: 2 }} />
                )}
                <Box sx={{ flexGrow: 1 }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                      Current Provider: {config?.provider === 'gemini' ? `Google ${config?.model || 'Gemini'}` : 'Smart Offline AI Engine'}
                    </Typography>
                    <Chip
                      label={config?.hasApiKey ? 'API Key Active' : 'Offline Mode'}
                      color={config?.hasApiKey ? 'success' : 'warning'}
                      size="small"
                      sx={{ height: 20, fontSize: 11, fontWeight: 700 }}
                    />
                  </Stack>
                  <Typography variant="body2" color="text.secondary" sx={{ fontSize: 13 }}>
                    {config?.statusMessage}
                  </Typography>
                </Box>
              </Stack>
            </Box>

            {/* Input Form */}
            <Stack spacing={2.5}>
              <TextField
                label="Gemini API Key"
                placeholder="AIzaSy..."
                fullWidth
                size="small"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                helperText={
                  config?.hasApiKey
                    ? `Current Active Key: ${config.maskedApiKey} (Leave blank to keep current key)`
                    : 'Get your free Gemini API Key at https://aistudio.google.com/app/apikey'
                }
                slotProps={{
                  input: {
                    startAdornment: <Key size={18} style={{ marginRight: 8, color: '#818CF8' }} />,
                  },
                }}
              />

              <TextField
                select
                label="Gemini Model Family"
                fullWidth
                size="small"
                value={model}
                onChange={(e) => setModel(e.target.value)}
              >
                <MenuItem value="gemini-2.0-flash">Gemini 2.0 Flash (Fast, Multimodal — Recommended)</MenuItem>
                <MenuItem value="gemini-2.0-flash-lite">Gemini 2.0 Flash Lite (Lightweight)</MenuItem>
                <MenuItem value="gemini-1.5-pro">Gemini 1.5 Pro (Deep Code Reasoning)</MenuItem>
                <MenuItem value="gemini-1.5-flash">Gemini 1.5 Flash (Standard)</MenuItem>
              </TextField>

              <Alert severity="info" icon={<Cpu size={18} />} sx={{ borderRadius: 2, fontSize: 12 }}>
                When no API Key is provided, QPilot AI automatically operates in <strong>Smart Offline Engine Mode</strong>, parsing real file contents and structure to generate project-tailored tests.
              </Alert>
            </Stack>
          </Stack>
        )}
      </DialogContent>

      <DialogActions sx={{ p: 2.5, borderColor: 'rgba(255, 255, 255, 0.08)' }}>
        <Button onClick={onClose} variant="outlined" sx={{ borderRadius: 2 }}>
          Close
        </Button>
        <Button
          onClick={handleSave}
          variant="contained"
          color="primary"
          startIcon={isSaving ? <CircularProgress size={16} color="inherit" /> : <RefreshCw size={16} />}
          disabled={isSaving}
          sx={{ borderRadius: 2, fontWeight: 700, px: 3 }}
        >
          {isSaving ? 'Updating Key…' : 'Save & Activate AI Key'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
