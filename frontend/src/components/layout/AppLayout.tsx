import { useCallback, useState, type ReactNode } from 'react';
import { Box } from '@mui/material';
import { Navbar } from './Navbar';
import { Sidebar, DRAWER_WIDTH } from './Sidebar';
import { CommandPalette, useCommandPaletteShortcut } from '../common/CommandPalette';
import { UploadProjectDialog } from '../../pages/dashboard/UploadProjectDialog';

interface AppLayoutProps {
  children: ReactNode;
  onRefreshProjects?: () => void;
}

/**
 * Application shell: sidebar, sticky navbar, and the scrolling content column.
 *
 * <p>The content column is constrained with `minWidth: 0`. Without it, a flex child refuses to shrink below
 * its content's intrinsic width, so one wide table or chart would push the whole layout sideways and give
 * the page a horizontal scrollbar — the single most common responsive failure in a dashboard like this.
 */
export function AppLayout({ children, onRefreshProjects }: AppLayoutProps) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);

  // The shortcut is registered here because this is where the palette's open state lives.
  useCommandPaletteShortcut(useCallback(() => setSearchOpen((open) => !open), []));

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Sidebar
        mobileOpen={mobileOpen}
        onClose={() => setMobileOpen(false)}
        onOpenUpload={() => setUploadOpen(true)}
        onOpenSearch={() => setSearchOpen(true)}
      />

      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          flexGrow: 1,
          minWidth: 0,
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          minHeight: '100vh',
        }}
      >
        <Box sx={{ position: 'sticky', top: 0, zIndex: (theme) => theme.zIndex.appBar }}>
          <Navbar onMobileDrawerToggle={() => setMobileOpen((open) => !open)} onOpenSearch={() => setSearchOpen(true)} />
        </Box>

        <Box component="main" sx={{ flexGrow: 1, px: { xs: 1.75, sm: 2.5, md: 3 }, py: { xs: 2, md: 3 }, minWidth: 0 }}>
          <Box sx={{ maxWidth: 1600, mx: 'auto', width: '100%', minWidth: 0 }}>{children}</Box>
        </Box>
      </Box>

      <CommandPalette open={searchOpen} onClose={() => setSearchOpen(false)} onOpenUpload={() => setUploadOpen(true)} />

      <UploadProjectDialog
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        onUploaded={() => {
          onRefreshProjects?.();
        }}
      />
    </Box>
  );
}
