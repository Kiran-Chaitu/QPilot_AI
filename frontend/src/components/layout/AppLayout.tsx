import { useState } from 'react';
import { Box } from '@mui/material';
import type { ReactNode } from 'react';
import { Navbar } from './Navbar';
import { Sidebar } from './Sidebar';
import { CommandPalette } from '../common/CommandPalette';
import { UploadProjectDialog } from '../../pages/dashboard/UploadProjectDialog';

const DRAWER_WIDTH = 260;

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
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      {/* Left Sidebar */}
      <Sidebar
        mobileOpen={mobileOpen}
        onClose={() => setMobileOpen(false)}
        onOpenUpload={handleOpenUpload}
        onOpenSearch={handleOpenSearch}
      />

      {/* Right content column: Navbar stacked above scrollable main */}
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          flexGrow: 1,
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          minHeight: '100vh',
        }}
      >
        {/* Fixed Navbar */}
        <Box sx={{ position: 'sticky', top: 0, zIndex: (theme) => theme.zIndex.drawer + 1 }}>
          <Navbar
            onMobileDrawerToggle={handleDrawerToggle}
            onOpenSearch={handleOpenSearch}
          />
        </Box>

        {/* Scrollable Main Content */}
        <Box
          component="main"
          sx={{
            flexGrow: 1,
            px: { xs: 2, md: 3 },
            py: 3,
            overflowY: disableScroll ? { xs: 'auto', lg: 'hidden' } : 'auto',
          }}
        >
          <Box sx={{ maxWidth: 1600, mx: 'auto', width: '100%' }}>
            {children}
          </Box>
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
