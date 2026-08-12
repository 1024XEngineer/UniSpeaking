import { Navigate, useNavigate } from 'react-router-dom'
import { AppShell } from './AppShell'
import { LoginPage } from '../features/auth/LoginPage'
import { useAuth } from '../features/auth/AuthContext'

export function LoginRoute() {
  const auth = useAuth()
  const navigate = useNavigate()
  if (!auth.pending && auth.administrator) {
    return <Navigate to="/" replace />
  }
  return <LoginPage login={auth.login} onAuthenticated={() => navigate('/', { replace: true })} />
}

export function AuthenticatedShell() {
  const auth = useAuth()
  if (!auth.administrator) {
    return null
  }
  return <AppShell administrator={auth.administrator} logout={auth.logout} />
}
