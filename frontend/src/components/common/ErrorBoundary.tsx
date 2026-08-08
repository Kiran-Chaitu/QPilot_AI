import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Box, Button, Card, Stack, Typography } from '@mui/material';
import { AlertOctagon, RefreshCw, Home } from 'lucide-react';

interface Props {
  children: ReactNode;
  /** Names the region being guarded, so the message can say what failed. */
  boundaryName?: string;
}

interface State {
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

/**
 * Catches render-time exceptions and shows a real explanation.
 *
 * <p>Without a boundary, any exception thrown during render unmounts the whole React tree and leaves a
 * blank white page — no message, no navigation, and nothing to click. That is the single worst failure
 * mode a UI can have, because the user cannot tell a crash from a slow load and has no way out.
 *
 * <p>Boundaries are placed at two levels: one around the entire app (so nothing can blank the page) and
 * one inside the routed content (so a crash in one page leaves the shell — sidebar, navigation, theme —
 * intact and the user can simply navigate elsewhere).
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null, errorInfo: null };

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    this.setState({ errorInfo });
    // Kept in the console deliberately: the component stack is the only practical way to locate the
    // offending component, and swallowing it would make production crashes undiagnosable.
    console.error(`[QPilot] Render error in ${this.props.boundaryName ?? 'application'}:`, error, errorInfo);
  }

  private handleReset = () => {
    this.setState({ error: null, errorInfo: null });
  };

  render() {
    const { error, errorInfo } = this.state;
    if (!error) {
      return this.props.children;
    }

    return (
      <Box sx={{ p: { xs: 2, md: 4 }, display: 'grid', placeItems: 'center', minHeight: '60vh' }}>
        <Card sx={{ maxWidth: 720, width: '100%', p: { xs: 3, md: 4 } }}>
          <Stack spacing={2.5}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <Box
                sx={{
                  width: 44,
                  height: 44,
                  borderRadius: 2.5,
                  display: 'grid',
                  placeItems: 'center',
                  bgcolor: 'rgba(240, 68, 82, 0.12)',
                  border: '1px solid rgba(240, 68, 82, 0.3)',
                }}
              >
                <AlertOctagon size={22} color="#F04452" />
              </Box>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  This view hit an unexpected error
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  The rest of QPilot is still working — you can retry this view or go back to the dashboard.
                </Typography>
              </Box>
            </Stack>

            <Box
              sx={{
                p: 1.75,
                borderRadius: 2.5,
                bgcolor: 'action.hover',
                border: '1px solid',
                borderColor: 'divider',
              }}
            >
              <Typography variant="caption" sx={{ fontWeight: 800, display: 'block', mb: 0.5 }}>
                ERROR DETAIL
              </Typography>
              <Typography
                variant="body2"
                sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.78rem', overflowWrap: 'anywhere' }}
              >
                {error.name}: {error.message}
              </Typography>
              {errorInfo?.componentStack && (
                <Box
                  component="pre"
                  sx={{
                    mt: 1.5,
                    maxHeight: 180,
                    overflow: 'auto',
                    fontSize: '0.7rem',
                    opacity: 0.75,
                    background: 'transparent',
                    border: 'none',
                    p: 0,
                  }}
                >
                  {errorInfo.componentStack.trim().split('\n').slice(0, 8).join('\n')}
                </Box>
              )}
            </Box>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <Button variant="contained" startIcon={<RefreshCw size={16} />} onClick={this.handleReset}>
                Retry this view
              </Button>
              <Button variant="outlined" startIcon={<Home size={16} />} onClick={() => { window.location.href = '/dashboard'; }}>
                Back to dashboard
              </Button>
              <Button variant="text" onClick={() => window.location.reload()}>
                Reload the app
              </Button>
            </Stack>
          </Stack>
        </Card>
      </Box>
    );
  }
}
