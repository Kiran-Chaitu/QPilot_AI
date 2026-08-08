import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Chip,
  Dialog,
  DialogContent,
  InputBase,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
} from '@mui/material';
import { CornerDownLeft, LayoutDashboard, Search, Upload } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { listProjects } from '../../api/projectApi';
import type { ProjectResponse } from '../../types/project';

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
  onOpenUpload?: () => void;
}

interface Command {
  id: string;
  title: string;
  subtitle: string;
  category: string;
  icon: React.ReactNode;
  perform: () => void;
}

/**
 * Command palette (⌘K / Ctrl+K).
 *
 * <p>The previous keyboard handler called `onClose()` in both branches of its open check, so the shortcut
 * could only ever close the palette — pressing ⌘K on the dashboard did nothing at all. Opening is owned by
 * the parent, so this component signals it through `onRequestOpen` instead of trying to toggle state it
 * does not hold.
 *
 * <p>Commands include the user's real projects, fetched when the palette opens, so search actually
 * navigates somewhere rather than listing a fixed set of tool links.
 */
export const CommandPalette: React.FC<CommandPaletteProps> = ({ open, onClose, onOpenUpload }) => {
  const [query, setQuery] = useState('');
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const navigate = useNavigate();

  // Loaded on open rather than on mount, so the list reflects projects added during the session.
  useEffect(() => {
    if (!open) {
      return;
    }
    setQuery('');
    listProjects()
      .then(setProjects)
      .catch(() => setProjects([]));
  }, [open]);

  const commands = useMemo<Command[]>(() => {
    const base: Command[] = [
      {
        id: 'dashboard',
        title: 'Go to dashboard',
        subtitle: 'Workspace metrics and project list',
        category: 'Navigation',
        icon: <LayoutDashboard size={18} />,
        perform: () => {
          navigate('/dashboard');
          onClose();
        },
      },
      {
        id: 'upload',
        title: 'Add a project',
        subtitle: 'Upload a source archive or point QPilot at a live URL',
        category: 'Actions',
        icon: <Upload size={18} />,
        perform: () => {
          onClose();
          onOpenUpload?.();
        },
      },
    ];

    const projectCommands: Command[] = projects.map((project) => ({
      id: `project-${project.id}`,
      title: project.name,
      subtitle: `${project.sourceType.replace('_', ' ')} · ${project.primaryLanguage ?? 'unknown language'} · ${project.status.toLowerCase()}`,
      category: 'Projects',
      icon: <Search size={18} />,
      perform: () => {
        navigate(`/projects/${project.id}`);
        onClose();
      },
    }));

    return [...base, ...projectCommands];
  }, [projects, navigate, onClose, onOpenUpload]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return commands;
    return commands.filter((command) =>
      [command.title, command.subtitle, command.category].some((field) => field.toLowerCase().includes(needle)),
    );
  }, [commands, query]);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      slotProps={{ paper: { sx: { overflow: 'hidden', mt: { xs: 2, sm: 8 }, alignSelf: 'flex-start' } } }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', px: 2, py: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
        <Search size={19} style={{ opacity: 0.6, marginRight: 12, flexShrink: 0 }} />
        <InputBase
          autoFocus
          placeholder="Search projects and actions…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && filtered.length > 0) {
              filtered[0].perform();
            }
          }}
          sx={{ flexGrow: 1, fontSize: '0.95rem', fontWeight: 550 }}
        />
        <Chip label="ESC" size="small" variant="outlined" sx={{ fontSize: '0.65rem', height: 20 }} />
      </Box>

      <DialogContent sx={{ p: 1, maxHeight: 420, overflowY: 'auto' }}>
        {filtered.length === 0 ? (
          <Box sx={{ p: 3, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              Nothing matches “{query}”.
            </Typography>
          </Box>
        ) : (
          <List disablePadding>
            {filtered.map((command, index) => {
              const showHeader = index === 0 || filtered[index - 1].category !== command.category;
              return (
                <React.Fragment key={command.id}>
                  {showHeader && (
                    <Typography variant="overline" color="text.secondary" sx={{ px: 2, pt: 1.5, pb: 0.5, display: 'block' }}>
                      {command.category}
                    </Typography>
                  )}
                  <ListItemButton onClick={command.perform} sx={{ borderRadius: 2.5, mx: 0.5, py: 1 }}>
                    <ListItemIcon sx={{ minWidth: 36 }}>{command.icon}</ListItemIcon>
                    <ListItemText
                      primary={
                        <Typography variant="body2" sx={{ fontWeight: 650 }}>
                          {command.title}
                        </Typography>
                      }
                      secondary={
                        <Typography variant="caption" color="text.secondary">
                          {command.subtitle}
                        </Typography>
                      }
                    />
                    {index === 0 && <CornerDownLeft size={13} style={{ opacity: 0.4 }} />}
                  </ListItemButton>
                </React.Fragment>
              );
            })}
          </List>
        )}
      </DialogContent>
    </Dialog>
  );
};

/**
 * Registers the global ⌘K / Ctrl+K shortcut.
 *
 * <p>Lives outside the palette component because the palette's open state belongs to the layout: a
 * component cannot open itself if it only receives `open` and `onClose`, which is why the original
 * shortcut could never work.
 */
export function useCommandPaletteShortcut(onToggle: () => void) {
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        onToggle();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onToggle]);
}
