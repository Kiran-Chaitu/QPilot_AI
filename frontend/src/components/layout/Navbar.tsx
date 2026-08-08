import React, { useCallback, useEffect, useState } from 'react';
import {
  AppBar,
  Avatar,
  Box,
  Button,
  Chip,
  Divider,
  IconButton,
  Menu,
  MenuItem,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import { LogOut, Menu as MenuIcon, Moon, Search, Settings, Shield, Sparkles, Sun } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useAppTheme } from '../../theme/ThemeContext';
import { getAiConfig, type AiConfig } from '../../api/aiApi';
import { AiSettingsModal } from '../common/AiSettingsModal';
import { brand } from '../../theme/palette';

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

  const fetchAiConfig = useCallback(async () => {
    try {
      setAiConfig(await getAiConfig());
    } catch {
      // The status chip is informational; a failure here must not break the navigation bar.
      setAiConfig(null);
    }
  }, []);

  useEffect(() => {
    fetchAiConfig();
  }, [fetchAiConfig]);

  const handleLogout = () => {
    setAnchorEl(null);
    logout();
    navigate('/login', { replace: true });
  };

  const aiConfigured = aiConfig?.provider === 'gemini';
  // Only an administrator can change the process-wide API key, so the control is hidden for everyone
  // else rather than shown and then rejected with a 403.
  const canConfigureAi = user?.role === 'ADMIN';

  return (
    <>
      <AppBar
        position="static"
        color="default"
        elevation={0}
        className="qp-glass"
        sx={{ borderBottom: '1px solid', borderColor: 'divider', backgroundImage: 'none' }}
      >
        <Toolbar sx={{ gap: 1, minHeight: 62, px: { xs: 1.5, md: 2.5 } }}>
          <IconButton onClick={onMobileDrawerToggle} sx={{ display: { md: 'none' } }} aria-label="Open navigation">
            <MenuIcon size={21} />
          </IconButton>

          <Box
            onClick={onOpenSearch}
            role="button"
            tabIndex={0}
            onKeyDown={(event) => {
              if (event.key === 'Enter') onOpenSearch();
            }}
            sx={{
              display: { xs: 'none', sm: 'flex' },
              alignItems: 'center',
              gap: 1.25,
              px: 1.75,
              py: 0.7,
              borderRadius: 2.5,
              bgcolor: 'action.hover',
              border: '1px solid',
              borderColor: 'divider',
              cursor: 'pointer',
              width: { sm: 240, md: 320 },
              transition: 'border-color 160ms ease',
              '&:hover': { borderColor: 'primary.main' },
            }}
          >
            <Search size={16} style={{ opacity: 0.6 }} />
            <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1, fontWeight: 500 }}>
              Search projects…
            </Typography>
            <Chip label="⌘K" size="small" variant="outlined" sx={{ height: 19, fontSize: '0.62rem' }} />
          </Box>

          <Box sx={{ flexGrow: 1 }} />

          {/* Reports the actual provider state — not a fixed "AI active" badge. */}
          <Tooltip title={aiConfig?.statusMessage ?? 'Checking AI provider configuration…'}>
            <Chip
              icon={<Sparkles size={13} color={aiConfigured ? brand.secondary : undefined} />}
              label={aiConfigured ? 'AI enrichment on' : 'Static analysis only'}
              color={aiConfigured ? 'secondary' : 'default'}
              variant="outlined"
              size="small"
              onClick={canConfigureAi ? () => setAiModalOpen(true) : undefined}
              sx={{ fontWeight: 700, display: { xs: 'none', sm: 'flex' }, cursor: canConfigureAi ? 'pointer' : 'help' }}
            />
          </Tooltip>

          <IconButton onClick={onOpenSearch} sx={{ display: { xs: 'flex', sm: 'none' } }} aria-label="Search">
            <Search size={19} />
          </IconButton>

          <Tooltip title={`Switch to ${mode === 'dark' ? 'light' : 'dark'} theme`}>
            <IconButton onClick={toggleTheme} aria-label="Toggle colour theme">
              {mode === 'dark' ? <Sun size={19} /> : <Moon size={19} />}
            </IconButton>
          </Tooltip>

          <Divider orientation="vertical" flexItem sx={{ mx: 0.75, my: 1.5, display: { xs: 'none', sm: 'block' } }} />

          {user ? (
            <>
              <Button
                onClick={(event) => setAnchorEl(event.currentTarget)}
                sx={{ p: 0.5, borderRadius: 2.5, color: 'text.primary', '&:hover': { bgcolor: 'action.hover' } }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
                  <Avatar
                    sx={{
                      width: 33,
                      height: 33,
                      fontWeight: 750,
                      fontSize: 13,
                      background: `linear-gradient(135deg, ${brand.primary} 0%, ${brand.secondary} 100%)`,
                      color: '#0B0C12',
                    }}
                  >
                    {user.fullName?.charAt(0).toUpperCase() ?? 'U'}
                  </Avatar>
                  <Box sx={{ textAlign: 'left', display: { xs: 'none', md: 'block' } }}>
                    <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                      {user.fullName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.7rem' }}>
                      {user.role.replace(/_/g, ' ')}
                    </Typography>
                  </Box>
                </Box>
              </Button>

              <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={() => setAnchorEl(null)}
                transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                slotProps={{ paper: { sx: { mt: 1.25, minWidth: 232, borderRadius: 3, border: '1px solid', borderColor: 'divider' } } }}
              >
                <Box sx={{ px: 2, py: 1.5 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 750 }}>
                    {user.fullName}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', overflowWrap: 'anywhere' }}>
                    {user.email}
                  </Typography>
                  <Chip icon={<Shield size={11} />} label={user.role.replace(/_/g, ' ')} size="small" color="primary" sx={{ mt: 1, height: 20, fontSize: '0.64rem' }} />
                </Box>
                <Divider />
                {canConfigureAi && (
                  <MenuItem
                    onClick={() => {
                      setAnchorEl(null);
                      setAiModalOpen(true);
                    }}
                  >
                    <Settings size={15} style={{ marginRight: 10 }} />
                    AI configuration
                  </MenuItem>
                )}
                <MenuItem onClick={handleLogout} sx={{ color: 'error.main' }}>
                  <LogOut size={15} style={{ marginRight: 10 }} />
                  Sign out
                </MenuItem>
              </Menu>
            </>
          ) : (
            <Button variant="contained" onClick={() => navigate('/login')}>
              Sign in
            </Button>
          )}
        </Toolbar>
      </AppBar>

      <AiSettingsModal open={aiModalOpen} onClose={() => setAiModalOpen(false)} onConfigChanged={fetchAiConfig} />
    </>
  );
};
