import React from 'react';
import {
  Box,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
  Chip,
  Divider,
  Button,
} from '@mui/material';
import {
  LayoutDashboard,
  Globe,
  Gauge,
  ShieldAlert,
  FileCode,
  FileText,
  Bot,
  Plus,
  Sparkles,
  Search,
} from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';

const DRAWER_WIDTH = 260;

interface SidebarProps {
  mobileOpen: boolean;
  onClose: () => void;
  onOpenUpload: () => void;
  onOpenSearch: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ mobileOpen, onClose, onOpenUpload, onOpenSearch }) => {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    { text: 'Dashboard', icon: <LayoutDashboard size={20} />, path: '/dashboard' },
    { text: 'Synthetic Web Auditor', icon: <Globe size={20} />, path: '/dashboard?tab=website' },
    { text: 'Safe Load Tester', icon: <Gauge size={20} />, path: '/dashboard?tab=loadtest' },
    { text: 'Security Audit', icon: <ShieldAlert size={20} />, path: '/dashboard?tab=security' },
    { text: 'AI Test Generators', icon: <FileCode size={20} />, path: '/dashboard?tab=tests' },
    { text: 'Quality Reports', icon: <FileText size={20} />, path: '/dashboard?tab=reports' },
  ];

  const isActive = (path: string) => {
    return location.pathname + location.search === path || (path === '/dashboard' && location.pathname === '/dashboard' && !location.search);
  };

  const drawerContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: 'background.paper' }}>
      {/* Brand Header */}
      <Box sx={{ p: 2.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Box
          sx={{
            width: 38,
            height: 38,
            borderRadius: '10px',
            background: 'linear-gradient(135deg, #10B981 0%, #A855F7 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 4px 14px rgba(16, 185, 129, 0.4)',
          }}
        >
          <Bot size={22} color="#09090B" />
        </Box>
        <Box sx={{ flexGrow: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="h6" sx={{ fontWeight: 800, fontSize: '1.15rem', letterSpacing: '-0.02em', lineHeight: 1 }}>
              QPilot AI
            </Typography>
            <Chip label="PRO" size="small" color="primary" sx={{ height: 18, fontSize: '0.65rem', fontWeight: 800 }} />
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.72rem' }}>
            Autonomous Quality Engineering
          </Typography>
        </Box>
      </Box>

      {/* Action Button */}
      <Box sx={{ px: 2, pb: 2 }}>
        <Button
          fullWidth
          variant="contained"
          color="primary"
          startIcon={<Plus size={18} />}
          onClick={onOpenUpload}
          sx={{ py: 1.2, fontWeight: 800, borderRadius: 2 }}
        >
          New Analysis
        </Button>
      </Box>

      {/* Search trigger pill */}
      <Box sx={{ px: 2, mb: 1 }}>
        <Box
          onClick={onOpenSearch}
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            px: 1.5,
            py: 0.8,
            borderRadius: 2,
            border: '1px solid',
            borderColor: 'divider',
            cursor: 'pointer',
            bgcolor: 'action.hover',
            '&:hover': { borderColor: 'primary.main' },
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Search size={16} style={{ opacity: 0.6 }} />
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
              Search everywhere...
            </Typography>
          </Box>
          <Chip label="⌘K" size="small" variant="outlined" sx={{ height: 18, fontSize: '0.65rem' }} />
        </Box>
      </Box>

      <Divider sx={{ mx: 2, my: 1 }} />

      {/* Navigation Links */}
      <List sx={{ px: 1.5, flexGrow: 1 }}>
        {menuItems.map((item) => {
          const active = isActive(item.path);
          return (
            <ListItem disablePadding key={item.text} sx={{ mb: 0.5 }}>
              <ListItemButton
                selected={active}
                onClick={() => {
                  navigate(item.path);
                  onClose();
                }}
                sx={{
                  borderRadius: 2,
                  py: 1,
                  color: active ? 'primary.main' : 'text.secondary',
                  bgcolor: active ? 'rgba(16, 185, 129, 0.12)' : 'transparent',
                  borderLeft: active ? '3px solid #10B981' : '3px solid transparent',
                  '&:hover': {
                    bgcolor: 'action.hover',
                    color: 'text.primary',
                  },
                }}
              >
                <ListItemIcon sx={{ minWidth: 36, color: active ? 'primary.main' : 'text.secondary' }}>
                  {item.icon}
                </ListItemIcon>
                <ListItemText
                  primary={
                    <Typography variant="body2" sx={{ fontWeight: active ? 800 : 500 }}>
                      {item.text}
                    </Typography>
                  }
                />
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>

      {/* AI Assistant Banner */}
      <Box sx={{ p: 2, m: 1.5, borderRadius: 3, bgcolor: 'rgba(16, 185, 129, 0.05)', border: '1px solid', borderColor: 'rgba(16, 185, 129, 0.2)' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
          <Sparkles size={18} color="#10B981" />
          <Typography variant="subtitle2" sx={{ fontWeight: 800, fontSize: '0.85rem' }}>
            AI Quality Engine
          </Typography>
        </Box>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5, lineHeight: 1.4 }}>
          Gemini 3.6 Multi-Agent pipeline active. Ready for automated test generation & risk audits.
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <span className="status-live-dot" />
          <Typography variant="caption" sx={{ fontWeight: 700, color: 'primary.main' }}>
            Systems Operational
          </Typography>
        </Box>
      </Box>
    </Box>
  );

  return (
    <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
      {/* Mobile Drawer */}
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
        {drawerContent}
      </Drawer>

      {/* Desktop Permanent Drawer */}
      <Drawer
        variant="permanent"
        sx={{
          display: { xs: 'none', md: 'block' },
          '& .MuiDrawer-paper': { boxSizing: 'border-box', width: DRAWER_WIDTH, borderRight: '1px solid', borderColor: 'divider' },
        }}
        open
      >
        {drawerContent}
      </Drawer>
    </Box>
  );
};
