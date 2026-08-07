import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  InputBase,
  Box,
  Typography,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Chip,
  Divider,
} from '@mui/material';
import {
  Search,
  LayoutDashboard,
  Upload,
  Globe,
  Gauge,
  ShieldCheck,
  FileCode2,
  Zap,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
  onOpenUpload?: () => void;
}

export const CommandPalette: React.FC<CommandPaletteProps> = ({ open, onClose, onOpenUpload }) => {
  const [query, setQuery] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        if (open) onClose();
        else onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose]);

  const actions = [
    {
      id: 'dash',
      title: 'Go to Dashboard',
      subtitle: 'Overview of all projects, KPIs & coverage metrics',
      icon: <LayoutDashboard size={20} color="#6366F1" />,
      category: 'Navigation',
      perform: () => {
        navigate('/dashboard');
        onClose();
      },
    },
    {
      id: 'upload',
      title: 'Upload Project / Import Spec',
      subtitle: 'ZIP, Git repository, OpenAPI/Swagger, Synthetic URL',
      icon: <Upload size={20} color="#10B981" />,
      category: 'Actions',
      perform: () => {
        onClose();
        if (onOpenUpload) onOpenUpload();
      },
    },
    {
      id: 'website',
      title: 'Synthetic Website Quality Auditor',
      subtitle: 'Crawl site for broken links, accessibility, SEO & performance',
      icon: <Globe size={20} color="#3B82F6" />,
      category: 'Tools',
      perform: () => {
        navigate('/dashboard?tab=website');
        onClose();
      },
    },
    {
      id: 'loadtest',
      title: 'Safe Load & Performance Engine',
      subtitle: 'Simulate concurrent user traffic, k6 & JMeter generators',
      icon: <Gauge size={20} color="#F59E0B" />,
      category: 'Tools',
      perform: () => {
        navigate('/dashboard?tab=loadtest');
        onClose();
      },
    },
    {
      id: 'security',
      title: 'Security & Vulnerability Audit',
      subtitle: 'JWT, CORS, SQLi, XSS, CSRF & Secret Scanning',
      icon: <ShieldCheck size={20} color="#EF4444" />,
      category: 'Tools',
      perform: () => {
        navigate('/dashboard?tab=security');
        onClose();
      },
    },
    {
      id: 'tests',
      title: 'AI Multi-Framework Test Generator',
      subtitle: 'JUnit, Mockito, RestAssured, Playwright, Cypress',
      icon: <FileCode2 size={20} color="#8B5CF6" />,
      category: 'Tools',
      perform: () => {
        navigate('/dashboard?tab=tests');
        onClose();
      },
    },
  ];

  const filtered = actions.filter(
    (a) =>
      a.title.toLowerCase().includes(query.toLowerCase()) ||
      a.subtitle.toLowerCase().includes(query.toLowerCase()) ||
      a.category.toLowerCase().includes(query.toLowerCase())
  );

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      slotProps={{
        paper: {
          sx: {
            borderRadius: 3,
            backgroundColor: 'background.paper',
            backgroundImage: 'none',
            border: '1px solid',
            borderColor: 'divider',
            boxShadow: '0 20px 50px rgba(0,0,0,0.5)',
            overflow: 'hidden',
          },
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', px: 2, py: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
        <Search size={20} style={{ opacity: 0.6, marginRight: 12 }} />
        <InputBase
          autoFocus
          placeholder="Search commands, tools, or projects... (Esc to close)"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          sx={{ flexGrow: 1, fontSize: '0.95rem', fontWeight: 500 }}
        />
        <Chip label="ESC" size="small" variant="outlined" sx={{ fontSize: '0.7rem', height: 20 }} />
      </Box>

      <DialogContent sx={{ p: 1, maxHeight: 400, overflowY: 'auto' }}>
        {filtered.length === 0 ? (
          <Box sx={{ p: 3, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              No matching commands found for "{query}"
            </Typography>
          </Box>
        ) : (
          <List disablePadding>
            {filtered.map((item, index) => (
              <React.Fragment key={item.id}>
                {index === 0 || filtered[index - 1].category !== item.category ? (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ px: 2, pt: 1.5, pb: 0.5, display: 'block', fontWeight: 700, letterSpacing: '0.05em' }}
                  >
                    {item.category.toUpperCase()}
                  </Typography>
                ) : null}
                <ListItemButton
                  onClick={item.perform}
                  sx={{
                    borderRadius: 2,
                    mx: 0.5,
                    py: 1,
                    '&:hover': {
                      backgroundColor: 'action.hover',
                    },
                  }}
                >
                  <ListItemIcon sx={{ minWidth: 38 }}>{item.icon}</ListItemIcon>
                  <ListItemText
                    primary={
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {item.title}
                      </Typography>
                    }
                    secondary={
                      <Typography variant="caption" color="text.secondary">
                        {item.subtitle}
                      </Typography>
                    }
                  />
                  <Zap size={14} style={{ opacity: 0.4 }} />
                </ListItemButton>
              </React.Fragment>
            ))}
          </List>
        )}
      </DialogContent>

      <Divider />
      <Box sx={{ px: 2, py: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'center', bgcolor: 'action.hover' }}>
        <Typography variant="caption" color="text.secondary">
          Tip: Press <code style={{ fontSize: '0.75rem' }}>Ctrl + K</code> anywhere to trigger
        </Typography>
        <Typography variant="caption" color="primary" sx={{ fontWeight: 600 }}>
          QPilot AI Engineer v2.0
        </Typography>
      </Box>
    </Dialog>
  );
};
