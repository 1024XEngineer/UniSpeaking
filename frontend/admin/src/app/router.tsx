import { createBrowserRouter } from 'react-router-dom'
import { AuthenticatedShell, LoginRoute } from './RouteComponents'
import { RequireAuth } from '../features/auth/RequireAuth'
import { OverviewPage } from '../features/overview/OverviewPage'
import { MonitoringPage } from '../features/governance/MonitoringPage'
import { ReconciliationPage } from '../features/governance/ReconciliationPage'
import { UsersPage } from '../features/governance/UsersPage'
import { SystemManagementPage } from '../features/system/SystemManagementPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginRoute /> },
  {
    element: <RequireAuth />,
    children: [{
      element: <AuthenticatedShell />,
      children: [
        { index: true, element: <OverviewPage /> },
        { path: 'users', element: <UsersPage /> },
        { path: 'monitoring', element: <MonitoringPage /> },
        { path: 'reconciliation', element: <ReconciliationPage /> },
        { path: 'system', element: <SystemManagementPage /> },
      ],
    }],
  },
], { basename: '/admin' })
