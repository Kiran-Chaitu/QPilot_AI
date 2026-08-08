import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  Divider,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Tooltip,
  Typography,
} from '@mui/material';
import { Bot, FolderKanban, LayoutDashboard, Plus, Search } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { getAiConfig, type AiConfig } from '../../api/aiApi';
import { brand } from '../../theme/palette';

export const DRAWER_WIDTH = 258;

interface SidebarProps {
  mobileOpen: boolean;
  onClose: () => void;
  onOpenUpload: () => void;
  onOpenSearch: () => void;
}

/**
 * Left navigation.
 *
 * <p>Navigation is limited to destinations that genuinely exist as routes. The previous sidebar linked to
 * six "tools" that were all query-string tabs on the dashboard, so the highlighted item frequently did not
 * match the page and several links led to panels that needed a project selected first. Tools now live where
 * they operate — inside a project — and the sidebar says so.
 *
 * <p>The engine status panel reports the real configured provider rather than a hardcoded claim: the
 * previous version stated "Gemini 3.6 Multi-Agent pipeline active" unconditionally, including on installs
 * with no API key at all.
 */
export const Sidebar: React.FC<SidebarProps> = ({ mobileOpen, onClose, onOpenUpload, onOpenSearch }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [aiConfig, setAiConfig] = useState<AiConfig | null>(null);

  useEffect(() => {
    getAiConfig()
      .then(setAiConfig)
      .catch(() => setAiConfig(null));
  }, []);

  const items = [
    { label: 'Dashboard', icon: <LayoutDashboard size={19} />, path: '/dashboard', match: (p: string) => p === '/dashboard' },
    { label: 'Projects', icon: <FolderKanban size={19} />, path: '/dashboard', match: (p: string) => p.startsWith('/projects') },
  ];

  const aiConfigured = aiConfig?.provider === 'gemini';

  const content = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: 'background.paper' }}>
      <Box sx={{ p: 2.25, display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Box
          sx={{
            width: 38,
            height: 38,
            borderRadius: '11px',
            background: `linear-gradient(135deg, ${brand.primary} 0%, ${brand.secondary} 100%)`,
            display: 'grid',
            placeItems: 'center',
            boxShadow: `0 6px 18px -8px ${brand.primary}`,
            flexShrink: 0,
          }}
        >
          <Bot size={21} color="#0B0C12" />
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h6" sx={{ fontWeight: 800, fontSize: '1.08rem', lineHeight: 1.15 }}>
            QPilot AI
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.7rem' }}>
            Quality engineering platform
          </Typography>
        </Box>
      </Box>

      <Box sx={{ px: 2, pb: 1.5 }}>
        <Button
          fullWidth
          variant="contained"
          startIcon={<Plus size={17} />}
          onClick={() => {
            onOpenUpload();
            onClose();
          }}
          sx={{ py: 1.05, fontWeight: 780 }}
        >
          Add project
        </Button>
      </Box>

      <Box sx={{ px: 2, mb: 1.25 }}>
        <Box
          onClick={() => {
            onOpenSearch();
            onClose();
          }}
          role="button"
          tabIndex={0}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              onOpenSearch();
              onClose();
            }
          }}
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            px: 1.4,
            py: 0.85,
            borderRadius: 2.5,
            border: '1px solid',
            borderColor: 'divider',
            cursor: 'pointer',
            bgcolor: 'action.hover',
            transition: 'border-color 160ms ease',
            '&:hover': { borderColor: 'primary.main' },
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Search size={15} style={{ opacity: 0.6 }} />
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 550 }}>
              Search
            </Typography>
          </Box>
          <Chip label="⌘K" size="small" variant="outlined" sx={{ height: 18, fontSize: '0.63rem' }} />
        </Box>
      </Box>

      <Divider sx={{ mx: 2 }} />

      <List sx={{ px: 1.25, py: 1.25, flexGrow: 1 }}>
        {items.map((item) => {
          const active = item.match(location.pathname);
          return (
            <ListItem disablePadding key={item.label} sx={{ mb: 0.4 }}>
              <ListItemButton
                selected={active}
                onClick={() => {
                  navigate(item.path);
                  onClose();
                }}
                sx={{
                  borderRadius: 2.5,
                  py: 0.95,
                  color: active ? 'primary.light' : 'text.secondary',
                  bgcolor: active ? 'action.selected' : 'transparent',
                  '&:hover': { bgcolor: 'action.hover', color: 'text.primary' },
                }}
              >
                <ListItemIcon sx={{ minWidth: 34, color: active ? 'primary.light' : 'text.secondary' }}>{item.icon}</ListItemIcon>
                <ListItemText
                  primary={
                    <Typography variant="body2" sx={{ fontWeight: active ? 780 : 550 }}>
                      {item.label}
                    </Typography>
                  }
                />
              </ListItemButton>
            </ListItem>
          );
        })}

        <Box sx={{ px: 1.5, pt: 2 }}>
          <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
            Testing tools
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.6 }}>
            Website audit, load testing, rate-limit probes and E2E checks all run against a specific project&apos;s
            target. Open a project to reach them.
          </Typography>
        </Box>
      </List>

      {/* Real engine status, driven by the configured provider rather than a fixed claim. */}
      <Tooltip title={aiConfig?.statusMessage ?? 'Checking AI provider configuration…'}>
        <Box
          sx={{
            p: 1.75,
            m: 1.5,
            borderRadius: 3,
            border: '1px solid',
            borderColor: aiConfigured ? 'rgba(18, 185, 129, 0.28)' : 'divider',
            bgcolor: aiConfigured ? 'rgba(18, 185, 129, 0.07)' : 'action.hover',
            cursor: 'help',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.75 }}>
            <span className={aiConfigured ? 'qp-live-dot qp-live-dot--success' : 'qp-live-dot qp-live-dot--idle'} />
            <Typography variant="subtitle2" sx={{ fontWeight: 780, fontSize: '0.8rem' }}>
              {aiConfigured ? 'AI enrichment on' : 'Static analysis only'}
            </Typography>
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.5 }}>
            {aiConfigured
              ? `${aiConfig?.model} is configured. AI output is labelled separately from measured results.`
              : 'No AI provider configured. Scanning, audits, load and rate-limit tests all work without one.'}
          </Typography>
        </Box>
      </Tooltip>
    </Box>
  );

  return (
    <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={onClose}
        ModalProps={{ keepMounted: true }}
        sx={{
          display: { xs: 'block', md: 'none' },
          '& .MuiDrawer-paper': { boxSizing: 'border-box', width: DRAWER_WIDTH, borderRight: '1px solid', borderColor: 'divider' },
        }}
      >
        {content}
      </Drawer>

      <Drawer
        variant="permanent"
        open
        sx={{
          display: { xs: 'none', md: 'block' },
          '& .MuiDrawer-paper': { boxSizing: 'border-box', width: DRAWER_WIDTH, borderRight: '1px solid', borderColor: 'divider' },
        }}
      >
        {content}
      </Drawer>
    </Box>
  );
};
