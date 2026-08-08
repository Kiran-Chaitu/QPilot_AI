import type { ReactNode } from 'react';
import { Box, Card, Chip, Stack, Typography } from '@mui/material';
import { Bot, FileSearch, Gauge, ShieldCheck } from 'lucide-react';
import { brand } from '../../theme/palette';

/**
 * Shared frame for the login and register pages.
 *
 * <p>The marketing column deliberately describes what QPilot measures rather than making capability
 * claims. Every bullet corresponds to something the product genuinely does, so a new user's expectations
 * match what they will actually see after signing in.
 */
export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'stretch' }}>
      {/* Context column — hidden on small screens, where the form is all that matters. */}
      <Box
        sx={{
          display: { xs: 'none', lg: 'flex' },
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 4,
          width: '46%',
          px: 8,
          borderRight: '1px solid',
          borderColor: 'divider',
          background:
            'radial-gradient(700px circle at 20% 20%, rgba(124, 92, 255, 0.14), transparent 55%),' +
            'radial-gradient(600px circle at 80% 80%, rgba(0, 194, 204, 0.10), transparent 55%)',
        }}
      >
        <Stack direction="row" spacing={1.75} sx={{ alignItems: 'center' }}>
          <Box
            sx={{
              width: 46,
              height: 46,
              borderRadius: '13px',
              background: `linear-gradient(135deg, ${brand.primary} 0%, ${brand.secondary} 100%)`,
              display: 'grid',
              placeItems: 'center',
            }}
          >
            <Bot size={25} color="#0B0C12" />
          </Box>
          <Box>
            <Typography variant="h5" sx={{ fontWeight: 800, lineHeight: 1.15 }}>
              QPilot AI
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Quality engineering platform
            </Typography>
          </Box>
        </Stack>

        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800, mb: 1.5, maxWidth: 460 }}>
            Testing results you can <Box component="span" className="qp-gradient-text">actually verify</Box>
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 440 }}>
            QPilot scans your real files, runs real requests against your real services, and reports what it measured —
            with the file, line and status code to back each claim up.
          </Typography>
        </Box>

        <Stack spacing={2.5}>
          {[
            {
              icon: <FileSearch size={19} color={brand.secondary} />,
              title: 'Evidence-backed findings',
              body: 'Every static finding cites the file and line it matched, so you can open it and confirm.',
            },
            {
              icon: <ShieldCheck size={19} color={brand.primary} />,
              title: 'Executed, not assumed',
              body: 'A test is only reported as passing after QPilot ran it and observed the response.',
            },
            {
              icon: <Gauge size={19} color={brand.primary} />,
              title: 'Real load measurements',
              body: 'Latency percentiles come from the full sample of completed requests, never a model.',
            },
          ].map((item) => (
            <Stack key={item.title} direction="row" spacing={1.75} sx={{ alignItems: 'flex-start' }}>
              <Box
                sx={{
                  p: 1,
                  borderRadius: 2.5,
                  bgcolor: 'action.hover',
                  border: '1px solid',
                  borderColor: 'divider',
                  flexShrink: 0,
                }}
              >
                {item.icon}
              </Box>
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 750 }}>
                  {item.title}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 380 }}>
                  {item.body}
                </Typography>
              </Box>
            </Stack>
          ))}
        </Stack>
      </Box>

      {/* Form column */}
      <Box sx={{ flexGrow: 1, display: 'grid', placeItems: 'center', p: { xs: 2.5, sm: 4 } }}>
        <Card className="qp-enter" sx={{ width: '100%', maxWidth: 456, p: { xs: 3, sm: 4 } }}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 3, display: { lg: 'none' } }}>
            <Box
              sx={{
                width: 38,
                height: 38,
                borderRadius: '11px',
                background: `linear-gradient(135deg, ${brand.primary} 0%, ${brand.secondary} 100%)`,
                display: 'grid',
                placeItems: 'center',
              }}
            >
              <Bot size={21} color="#0B0C12" />
            </Box>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              QPilot AI
            </Typography>
            <Chip size="small" variant="outlined" label="QA platform" sx={{ fontWeight: 700 }} />
          </Stack>

          <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>
            {title}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            {subtitle}
          </Typography>

          {children}
        </Card>
      </Box>
    </Box>
  );
}
