import { Box, Button, Card, Stack, Typography } from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import { Compass, Home, ArrowLeft } from 'lucide-react';

/**
 * The 404 page.
 *
 * <p>Unknown routes previously redirected straight to the dashboard, which quietly swallowed the
 * mistake: a mistyped or stale link looked like it worked and simply landed somewhere else, giving the
 * user no way to notice the URL was wrong. This states what happened, shows the path that failed, and
 * offers a way onward.
 */
export function NotFoundPage() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
      <Card className="qp-enter" sx={{ maxWidth: 560, width: '100%', p: { xs: 3, md: 5 }, textAlign: 'center' }}>
        <Stack spacing={2.5} sx={{ alignItems: 'center' }}>
          <Box
            sx={{
              width: 60,
              height: 60,
              borderRadius: '50%',
              display: 'grid',
              placeItems: 'center',
              bgcolor: 'action.hover',
              border: '1px solid',
              borderColor: 'divider',
            }}
          >
            <Compass size={28} strokeWidth={1.75} />
          </Box>

          <Box>
            <Typography variant="h3" className="qp-gradient-text" sx={{ fontWeight: 800, mb: 0.5 }}>
              404
            </Typography>
            <Typography variant="h6" sx={{ fontWeight: 750 }}>
              This page does not exist
            </Typography>
          </Box>

          <Typography variant="body2" color="text.secondary">
            Nothing is routed at{' '}
            <Box component="code" sx={{ fontSize: '0.8rem' }}>
              {location.pathname}
            </Box>
            . The link may be out of date, or the address may have a typo.
          </Typography>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ pt: 0.5, width: '100%', justifyContent: 'center' }}>
            <Button variant="contained" startIcon={<Home size={16} />} onClick={() => navigate('/dashboard')}>
              Go to dashboard
            </Button>
            <Button variant="outlined" startIcon={<ArrowLeft size={16} />} onClick={() => navigate(-1)}>
              Go back
            </Button>
          </Stack>
        </Stack>
      </Card>
    </Box>
  );
}
