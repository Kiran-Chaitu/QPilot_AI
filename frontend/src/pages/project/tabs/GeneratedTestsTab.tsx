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
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { Copy, Check, Download, FileCode } from 'lucide-react';
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
      <Box sx={{ textAlign: 'center', py: 6 }}>
        <FileCode size={48} color="#6366F1" style={{ marginBottom: 12 }} />
        <Typography variant="h6" sx={{ fontWeight: 700 }}>
          No Tests Generated Yet
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Run AI analysis from the Overview tab to generate unit, API, integration, and security test suites.
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Tabs
        value={activeType}
        onChange={(_, value) => setActiveType(value)}
        sx={{ mb: 2 }}
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

      <Stack spacing={1.5}>
        {filtered.map((test) => (
          <Accordion key={test.id} variant="outlined" disableGutters sx={{ borderRadius: 2, overflow: 'hidden' }}>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Stack direction="row" spacing={1.5} sx={{ width: '100%', pr: 2, alignItems: 'center' }}>
                <Chip size="small" label={TYPE_LABELS[test.type]} color="primary" variant="outlined" sx={{ fontWeight: 600 }} />
                <Typography sx={{ flexGrow: 1, fontWeight: 600 }}>{test.title}</Typography>
                <Chip size="small" label={test.framework} color="secondary" />
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                <Typography variant="body2" color="text.secondary">
                  Target Component / Endpoint: <code>{test.targetName}</code>
                </Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={copiedId === test.id ? <Check size={14} /> : <Copy size={14} />}
                    onClick={() => handleCopyCode(test.id, test.code)}
                  >
                    {copiedId === test.id ? 'Copied' : 'Copy Code'}
                  </Button>
                  <Button
                    size="small"
                    variant="contained"
                    color="primary"
                    startIcon={<Download size={14} />}
                    onClick={() => handleDownloadCode(test)}
                  >
                    Download File
                  </Button>
                </Stack>
              </Box>
              <Typography variant="body2" sx={{ mb: 2, color: 'text.secondary' }}>
                {test.description}
              </Typography>
              <Box
                component="pre"
                sx={{
                  bgcolor: '#0d1117',
                  color: '#c9d1d9',
                  p: 2.5,
                  borderRadius: 2,
                  overflowX: 'auto',
                  fontSize: 13,
                  fontFamily: 'JetBrains Mono, Consolas, Monaco, monospace',
                  border: '1px solid rgba(255,255,255,0.1)',
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
