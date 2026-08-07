import { useMemo, useState } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Chip,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import type { GeneratedTestResponse, TestType } from '../../../types/analysis';

const TYPE_LABELS: Record<TestType, string> = {
  UNIT: 'Unit',
  API: 'API',
  INTEGRATION: 'Integration',
  SECURITY: 'Security',
  EDGE_CASE: 'Edge Case',
};

export function GeneratedTestsTab({ tests }: { tests: GeneratedTestResponse[] }) {
  const [activeType, setActiveType] = useState<TestType | 'ALL'>('ALL');

  const filtered = useMemo(
    () => (activeType === 'ALL' ? tests : tests.filter((t) => t.type === activeType)),
    [tests, activeType],
  );

  const counts = useMemo(() => {
    const map = new Map<TestType, number>();
    tests.forEach((t) => map.set(t.type, (map.get(t.type) ?? 0) + 1));
    return map;
  }, [tests]);

  if (tests.length === 0) {
    return (
      <Typography color="text.secondary">
        No tests generated yet. Run analysis from the Overview tab.
      </Typography>
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
        <Tab value="ALL" label={`All (${tests.length})`} />
        {(Object.keys(TYPE_LABELS) as TestType[])
          .filter((type) => counts.has(type))
          .map((type) => (
            <Tab key={type} value={type} label={`${TYPE_LABELS[type]} (${counts.get(type)})`} />
          ))}
      </Tabs>

      <Stack spacing={1.5}>
        {filtered.map((test) => (
          <Accordion key={test.id} variant="outlined" disableGutters>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Stack direction="row" spacing={1.5} sx={{ width: '100%', pr: 2, alignItems: 'center' }}>
                <Chip size="small" label={TYPE_LABELS[test.type]} color="primary" variant="outlined" />
                <Typography sx={{ flexGrow: 1 }}>{test.title}</Typography>
                <Chip size="small" label={test.framework} variant="outlined" />
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Target: <code>{test.targetName}</code>
              </Typography>
              <Typography variant="body2" sx={{ mb: 2 }}>
                {test.description}
              </Typography>
              <Box
                component="pre"
                sx={{
                  bgcolor: '#0d1117',
                  color: '#c9d1d9',
                  p: 2,
                  borderRadius: 1,
                  overflowX: 'auto',
                  fontSize: 13,
                  fontFamily: 'Consolas, Monaco, monospace',
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
