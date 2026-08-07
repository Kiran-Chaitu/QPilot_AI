import { useMemo, useState } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  Chip,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import { Copy, Check, Download, FileCode, ChevronDown } from 'lucide-react';
import { useToast } from '../../../context/ToastContext';
import type { GeneratedTestResponse, TestType } from '../../../types/analysis';

const TYPE_LABELS: Record<TestType, string> = {
  UNIT: 'Unit',
  API: 'API',
  INTEGRATION: 'Integration',
  SECURITY: 'Security',
  EDGE_CASE: 'Edge Case',
};

export function GeneratedTestsTab({ tests }: { tests: GeneratedTestResponse[] }) {
  const { showSuccess } = useToast();
  const [activeType, setActiveType] = useState<TestType | 'ALL'>('ALL');
  const [copiedId, setCopiedId] = useState<number | null>(null);

  const filtered = useMemo(
    () => (activeType === 'ALL' ? tests : tests.filter((t) => t.type === activeType)),
    [tests, activeType]
  );

  const counts = useMemo(() => {
    const map = new Map<TestType, number>();
    tests.forEach((t) => map.set(t.type, (map.get(t.type) ?? 0) + 1));
    return map;
  }, [tests]);

  const handleCopyCode = (id: number, code: string) => {
    navigator.clipboard.writeText(code);
    setCopiedId(id);
    showSuccess('Code copied to clipboard!');
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleDownloadCode = (test: GeneratedTestResponse) => {
    const ext = test.framework.toLowerCase().includes('playwright') || test.framework.toLowerCase().includes('cypress') ? '.ts' : '.java';
    const filename = `${test.targetName.replace(/[^a-zA-Z0-9]/g, '')}Test${ext}`;
    const blob = new Blob([test.code], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    showSuccess(`Downloaded ${filename}`);
  };

  if (tests.length === 0) {
    return (
      <Box sx={{ textAlign: 'center', py: 8 }}>
        <FileCode size={48} color="#10B981" style={{ marginBottom: 12, opacity: 0.8 }} />
        <Typography variant="h6" sx={{ fontWeight: 800 }}>
          No Test Suites Generated Yet
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 400, mx: 'auto', mt: 0.5 }}>
          Run AI Multi-Agent Audit from the Overview tab to generate unit, API, integration, and security test suites.
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Tabs
        value={activeType}
        onChange={(_, value) => setActiveType(value)}
        sx={{ mb: 2.5 }}
        variant="scrollable"
        scrollButtons="auto"
      >
        <Tab value="ALL" label={`All Suites (${tests.length})`} />
        {(Object.keys(TYPE_LABELS) as TestType[])
          .filter((type) => counts.has(type))
          .map((type) => (
            <Tab key={type} value={type} label={`${TYPE_LABELS[type]} (${counts.get(type)})`} />
          ))}
      </Tabs>

      <Stack spacing={2}>
        {filtered.map((test) => (
          <Accordion
            key={test.id}
            variant="outlined"
            disableGutters
            sx={{
              borderRadius: 3,
              overflow: 'hidden',
              border: '1px solid',
              borderColor: 'divider',
              '&:before': { display: 'none' },
            }}
          >
            <AccordionSummary expandIcon={<ChevronDown size={18} />}>
              <Stack direction="row" spacing={1.5} sx={{ width: '100%', pr: 2, alignItems: 'center' }}>
                <Chip size="small" label={TYPE_LABELS[test.type]} color="primary" variant="outlined" sx={{ fontWeight: 800 }} />
                <Typography sx={{ flexGrow: 1, fontWeight: 700 }}>{test.title}</Typography>
                <Chip size="small" label={test.framework} color="secondary" sx={{ fontWeight: 700 }} />
              </Stack>
            </AccordionSummary>
            <AccordionDetails sx={{ p: 2.5, bgcolor: 'action.hover' }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2, flexWrap: 'wrap', gap: 1 }}>
                <Typography variant="body2" color="text.secondary">
                  Target Component / Endpoint: <code>{test.targetName}</code>
                </Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={copiedId === test.id ? <Check size={14} /> : <Copy size={14} />}
                    onClick={() => handleCopyCode(test.id, test.code)}
                    sx={{ fontWeight: 700 }}
                  >
                    {copiedId === test.id ? 'Copied' : 'Copy Code'}
                  </Button>
                  <Button
                    size="small"
                    variant="contained"
                    color="primary"
                    startIcon={<Download size={14} />}
                    onClick={() => handleDownloadCode(test)}
                    sx={{ fontWeight: 700 }}
                  >
                    Download File
                  </Button>
                </Stack>
              </Box>

              <Typography variant="body2" sx={{ mb: 2, color: 'text.secondary', lineHeight: 1.5 }}>
                {test.description}
              </Typography>

              <Box
                component="pre"
                sx={{
                  bgcolor: '#09090B',
                  color: '#FAFAFA',
                  p: 2.5,
                  borderRadius: 2.5,
                  overflowX: 'auto',
                  fontSize: '0.85rem',
                  fontFamily: 'JetBrains Mono, monospace',
                  border: '1px solid rgba(255,255,255,0.08)',
                }}
              >
                {test.code}
              </Box>
            </AccordionDetails>
          </Accordion>
        ))}
      </Stack>
    </Box>
  );
}
