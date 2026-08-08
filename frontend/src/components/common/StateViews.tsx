import type { ReactNode } from 'react';
import { Alert, AlertTitle, Box, Button, Card, Skeleton, Stack, Tooltip, Typography } from '@mui/material';
import { AlertTriangle, HelpCircle, Inbox, RefreshCw } from 'lucide-react';

/**
 * The state components every data-bearing view is built from.
 *
 * <p>These exist so the four outcomes of loading data — pending, empty, failed, unavailable — each have
 * one canonical rendering. The alternative is what the app had before: each page inventing its own
 * handling, several forgetting the failure case entirely, and a failed request rendering as a blank
 * region the user cannot distinguish from "nothing to show".
 */

interface ErrorStateProps {
  title?: string;
  /** The specific reason, ideally the server's own message. Never a generic "an error occurred". */
  message: string;
  onRetry?: () => void;
  retryLabel?: string;
  /** Extra guidance on what the user can actually do about it. */
  hint?: ReactNode;
  compact?: boolean;
}

/**
 * A failure that the user should see and can usually act on.
 *
 * <p>Always renders the concrete reason and, where the caller supplies one, a retry affordance —
 * the two things a user needs and a blank screen provides neither of.
 */
export function ErrorState({ title = 'Could not load this', message, onRetry, retryLabel = 'Retry', hint, compact }: ErrorStateProps) {
  return (
    <Alert
      severity="error"
      variant="outlined"
      icon={<AlertTriangle size={20} />}
      sx={{ borderRadius: 3, ...(compact ? { py: 0.75 } : { p: 2 }) }}
      action={
        onRetry ? (
          <Button color="inherit" size="small" startIcon={<RefreshCw size={14} />} onClick={onRetry} sx={{ fontWeight: 700 }}>
            {retryLabel}
          </Button>
        ) : undefined
      }
    >
      {!compact && <AlertTitle sx={{ fontWeight: 750 }}>{title}</AlertTitle>}
      <Typography variant="body2" sx={{ opacity: 0.95, overflowWrap: 'anywhere' }}>
        {message}
      </Typography>
      {hint && (
        <Typography variant="caption" sx={{ display: 'block', mt: 1, opacity: 0.8 }}>
          {hint}
        </Typography>
      )}
    </Alert>
  );
}

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  /** What the user should do to populate this view. An empty state without a next step is a dead end. */
  description?: ReactNode;
  action?: ReactNode;
  dense?: boolean;
}

export function EmptyState({ icon, title, description, action, dense }: EmptyStateProps) {
  return (
    <Box
      className="qp-enter"
      sx={{
        textAlign: 'center',
        py: dense ? 4 : 7,
        px: 3,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 1,
      }}
    >
      <Box
        sx={{
          width: 52,
          height: 52,
          borderRadius: '50%',
          display: 'grid',
          placeItems: 'center',
          bgcolor: 'action.hover',
          border: '1px solid',
          borderColor: 'divider',
          mb: 0.5,
        }}
      >
        {icon ?? <Inbox size={24} strokeWidth={1.75} />}
      </Box>
      <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
        {title}
      </Typography>
      {description && (
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 460 }}>
          {description}
        </Typography>
      )}
      {action && <Box sx={{ mt: 1.5 }}>{action}</Box>}
    </Box>
  );
}

/**
 * Marks a value QPilot did not measure.
 *
 * <p>This is a deliberate, load-bearing component. A metric that could not be determined must not
 * render as 0, "—", or a plausible default: those all read as measurements. It renders as explicit
 * unavailability with the reason on hover, so the distinction between "we measured zero" and "we could
 * not measure this" survives all the way to the screen.
 */
export function NotAvailable({ reason, inline }: { reason: string; inline?: boolean }) {
  return (
    <Tooltip title={reason}>
      <Box
        component="span"
        sx={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 0.5,
          color: 'text.disabled',
          fontSize: inline ? 'inherit' : '0.82rem',
          fontWeight: 600,
          cursor: 'help',
          borderBottom: '1px dashed',
          borderColor: 'divider',
        }}
      >
        Not available
        <HelpCircle size={13} />
      </Box>
    </Tooltip>
  );
}

/** Skeleton grid used while a card region loads, sized to match the content it stands in for. */
export function LoadingCards({ count = 4, height = 110 }: { count?: number; height?: number }) {
  return (
    <Box
      sx={{
        display: 'grid',
        gap: 2,
        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: `repeat(${Math.min(count, 4)}, 1fr)` },
      }}
    >
      {Array.from({ length: count }).map((_, index) => (
        <Skeleton key={index} variant="rounded" height={height} sx={{ borderRadius: 4 }} />
      ))}
    </Box>
  );
}

export function LoadingBlock({ height = 260, label }: { height?: number; label?: string }) {
  return (
    <Card sx={{ p: 2.5 }}>
      <Stack spacing={1.5}>
        {label && (
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
            {label}
          </Typography>
        )}
        <Skeleton variant="rounded" height={height} sx={{ borderRadius: 3 }} />
      </Stack>
    </Card>
  );
}
