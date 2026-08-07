import { useState } from 'react';
import { Box } from '@mui/material';
import type { ReactNode } from 'react';
import { Navbar } from './Navbar';
import { Sidebar } from './Sidebar';
import { CommandPalette } from '../common/CommandPalette';
import { UploadProjectDialog } from '../../pages/dashboard/UploadProjectDialog';

interface AppLayoutProps {
  children: ReactNode;
  onRefreshProjects?: () => void;
  disableScroll?: boolean;
}

export function AppLayout({ children, onRefreshProjects, disableScroll = false }: AppLayoutProps) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);

  const handleDrawerToggle = () => setMobileOpen(!mobileOpen);
  const handleOpenSearch = () => setSearchOpen(true);
  const handleCloseSearch = () => setSearchOpen(false);
  const handleOpenUpload = () => setUploadOpen(true);
  const handleCloseUpload = () => setUploadOpen(false);

  return (
    <Box sx={{ display: 'flex', height: '100vh', width: '100vw', overflow: 'hidden', bgcolor: 'background.default' }}>
      {/* Top Header Navbar */}
      <Navbar
        onMobileDrawerToggle={handleDrawerToggle}
        onOpenSearch={handleOpenSearch}
      />

      {/* Left Sidebar */}
      <Sidebar
        mobileOpen={mobileOpen}
        onClose={() => setMobileOpen(false)}
        onOpenUpload={handleOpenUpload}
        onOpenSearch={handleOpenSearch}
      />

      {/* Main Content Area */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          pt: '64px',
          p: { xs: 2, md: 3 },
          width: { md: `calc(100% - 260px)` },
          height: '100vh',
          display: 'flex',
          flexDirection: 'column',
          overflowY: disableScroll ? { xs: 'auto', lg: 'hidden' } : 'auto',
        }}
      >
        <Box sx={{ flexGrow: 1, maxWidth: 1600, mx: 'auto', width: '100%', display: 'flex', flexDirection: 'column' }}>
          {children}
        </Box>
      </Box>

      {/* Global Command Palette (Ctrl+K) */}
      <CommandPalette
        open={searchOpen}
        onClose={handleCloseSearch}
        onOpenUpload={handleOpenUpload}
      />

      {/* Enterprise Upload Modal */}
      <UploadProjectDialog
        open={uploadOpen}
        onClose={handleCloseUpload}
        onUploaded={() => {
          if (onRefreshProjects) onRefreshProjects();
        }}
      />
    </Box>
  );
}
