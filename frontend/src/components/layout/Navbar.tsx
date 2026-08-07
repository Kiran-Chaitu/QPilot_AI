import React, { useEffect, useState } from 'react';
import {
  AppBar,
  Avatar,
  Box,
  Button,
  Chip,
  IconButton,
  Menu,
  MenuItem,
  Toolbar,
  Typography,
  Tooltip,
  Divider,
} from '@mui/material';
import {
  Menu as MenuIcon,
  Search,
  Sun,
  Moon,
  Bell,
  LogOut,
  Shield,
  Sparkles,
  Settings,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useAppTheme } from '../../theme/ThemeContext';
import { getAiConfig, type AiConfig } from '../../api/aiApi';
import { AiSettingsModal } from '../common/AiSettingsModal';

interface NavbarProps {
  onMobileDrawerToggle: () => void;
  onOpenSearch: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ onMobileDrawerToggle, onOpenSearch }) => {
  const { user, logout } = useAuth();
  const { mode, toggleTheme } = useAppTheme();
  const navigate = useNavigate();

  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [aiConfig, setAiConfig] = useState<AiConfig | null>(null);
  const [aiModalOpen, setAiModalOpen] = useState(false);

  useEffect(() => {
    fetchAiConfig();
  }, []);

  async function fetchAiConfig() {
    try {
      const config = await getAiConfig();
      setAiConfig(config);
    } catch {
      // ignore
    }
  }

  const handleOpenMenu = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleCloseMenu = () => {
    setAnchorEl(null);
  };

  const handleLogout = () => {
    handleCloseMenu();
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <>
      <AppBar
        position="sticky"
        color="default"
        elevation={0}
        sx={{
          bgcolor: 'background.paper',
          borderBottom: '1px solid',
          borderColor: 'divider',
          backdropFilter: 'blur(12px)',
          zIndex: (theme) => theme.zIndex.drawer + 1,
        }}
      >
        <Toolbar sx={{ gap: 1, minHeight: 64, px: { xs: 2, md: 3 } }}>
          {/* Mobile menu toggle */}
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={onMobileDrawerToggle}
            sx={{ display: { md: 'none' }, mr: 1 }}
          >
            <MenuIcon size={22} />
          </IconButton>

          {/* Global Search Bar Button */}
          <Box
            onClick={onOpenSearch}
            sx={{
              display: { xs: 'none', sm: 'flex' },
              alignItems: 'center',
              gap: 1.5,
              px: 2,
              py: 0.8,
              borderRadius: 3,
              bgcolor: 'action.hover',
              border: '1px solid',
              borderColor: 'divider',
              cursor: 'pointer',
              width: { sm: 260, md: 320 },
              transition: 'all 0.2s ease',
              '&:hover': {
                borderColor: 'primary.main',
                bgcolor: 'action.selected',
              },
            }}
          >
            <Search size={18} style={{ opacity: 0.6 }} />
            <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1, fontWeight: 500 }}>
              Search projects, tests, tools...
            </Typography>
            <Chip label="⌘K" size="small" variant="outlined" sx={{ height: 20, fontSize: '0.65rem' }} />
          </Box>

          <Box sx={{ flexGrow: 1 }} />

          {/* AI Engine Status Button */}
          <Tooltip title="Click to configure Gemini API Key or AI Model">
            <Chip
              icon={<Sparkles size={14} color={aiConfig?.provider === 'gemini' ? '#10B981' : '#6366F1'} />}
              label={aiConfig?.provider === 'gemini' ? 'Gemini AI Active' : 'Smart Offline AI'}
              color={aiConfig?.provider === 'gemini' ? 'success' : 'primary'}
              variant="outlined"
              size="small"
              onClick={() => setAiModalOpen(true)}
              sx={{
                fontWeight: 700,
                cursor: 'pointer',
                px: 0.5,
                transition: 'all 0.2s ease',
                '&:hover': { transform: 'scale(1.03)' },
              }}
            />
          </Tooltip>

          {/* Settings Trigger */}
          <Tooltip title="AI & System Settings">
            <IconButton onClick={() => setAiModalOpen(true)} color="inherit">
              <Settings size={20} />
            </IconButton>
          </Tooltip>

          {/* Search button mobile */}
          <IconButton onClick={onOpenSearch} sx={{ display: { xs: 'flex', sm: 'none' } }}>
            <Search size={20} />
          </IconButton>

          {/* Theme Toggle Button */}
          <Tooltip title={`Switch to ${mode === 'dark' ? 'Light' : 'Dark'} Mode`}>
            <IconButton onClick={toggleTheme} color="inherit">
              {mode === 'dark' ? <Sun size={20} color="#F59E0B" /> : <Moon size={20} color="#6366F1" />}
            </IconButton>
          </Tooltip>

          {/* Notifications */}
          <Tooltip title="Notifications">
            <IconButton color="inherit">
              <Bell size={20} />
            </IconButton>
          </Tooltip>

          <Divider orientation="vertical" flexItem sx={{ mx: 1, my: 1.5 }} />

          {/* User Profile */}
          {user ? (
            <>
              <Button
                onClick={handleOpenMenu}
                sx={{
                  p: 0.5,
                  borderRadius: 3,
                  color: 'text.primary',
                  '&:hover': { bgcolor: 'action.hover' },
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, textTransform: 'none' }}>
                  <Avatar
                    sx={{
                      width: 36,
                      height: 36,
                      background: 'linear-gradient(135deg, #6366F1 0%, #10B981 100%)',
                      fontWeight: 700,
                      fontSize: 14,
                    }}
                  >
                    {user.fullName ? user.fullName.charAt(0).toUpperCase() : 'U'}
                  </Avatar>
                  <Box sx={{ textAlign: 'left', display: { xs: 'none', sm: 'block' } }}>
                    <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                      {user.fullName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.72rem' }}>
                      {user.role.replace('_', ' ')}
                    </Typography>
                  </Box>
                </Box>
              </Button>

              <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={handleCloseMenu}
                onClick={handleCloseMenu}
                slotProps={{
                  paper: {
                    sx: {
                      mt: 1.5,
                      minWidth: 200,
                      borderRadius: 3,
                      boxShadow: '0 10px 30px rgba(0,0,0,0.2)',
                      border: '1px solid',
                      borderColor: 'divider',
                    },
                  },
                }}
                transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
              >
                <Box sx={{ px: 2, py: 1.5 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    {user.fullName}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {user.email}
                  </Typography>
                  <Chip
                    icon={<Shield size={12} />}
                    label={user.role}
                    size="small"
                    color="primary"
                    sx={{ mt: 1, height: 20, fontSize: '0.65rem' }}
                  />
                </Box>
                <Divider />
                <MenuItem onClick={() => setAiModalOpen(true)}>
                  <Settings size={16} style={{ marginRight: 10 }} />
                  AI Settings & API Key
                </MenuItem>
                <Divider />
                <MenuItem onClick={handleLogout} sx={{ color: 'error.main' }}>
                  <LogOut size={16} style={{ marginRight: 10 }} />
                  Logout
                </MenuItem>
              </Menu>
            </>
          ) : (
            <Button variant="contained" color="primary" onClick={() => navigate('/login')}>
              Sign In
            </Button>
          )}
        </Toolbar>
      </AppBar>

      {/* AI Settings Dialog */}
      <AiSettingsModal
        open={aiModalOpen}
        onClose={() => setAiModalOpen(false)}
        onConfigChanged={fetchAiConfig}
      />
    </>
  );
};
