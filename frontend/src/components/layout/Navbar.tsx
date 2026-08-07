import { AppBar, Avatar, Box, Button, Chip, Toolbar, Typography } from '@mui/material';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <AppBar position="sticky" color="default" elevation={0} sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>
      <Toolbar sx={{ gap: 2 }}>
        <SmartToyIcon color="primary" />
        <Typography
          variant="h6"
          component="div"
          sx={{ flexGrow: 1, cursor: 'pointer', fontWeight: 700 }}
          onClick={() => navigate('/dashboard')}
        >
          AI TestPilot
        </Typography>
        {user && (
          <>
            <Chip size="small" label={user.role.replace('_', ' ')} color="secondary" variant="outlined" />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main', fontSize: 14 }}>
                {user.fullName.charAt(0).toUpperCase()}
              </Avatar>
              <Typography variant="body2">{user.fullName}</Typography>
            </Box>
            <Button size="small" onClick={handleLogout}>
              Logout
            </Button>
          </>
        )}
      </Toolbar>
    </AppBar>
  );
}
