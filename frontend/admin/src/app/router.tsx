import { Navigate, createBrowserRouter } from 'react-router-dom'
import { AuthenticatedShell, LoginRoute } from './RouteComponents'
import { RequireAuth } from '../features/auth/RequireAuth'
import { OverviewPage } from '../features/overview/OverviewPage'
import { BillingPage } from '../features/billing/BillingPage'
import { UsersPage } from '../features/governance/UsersPage'
import { SystemManagementPage } from '../features/system/SystemManagementPage'
import { QualityPage } from '../features/quality/QualityPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginRoute /> },
  {
    element: <RequireAuth />,
    children: [{
      element: <AuthenticatedShell />,
      children: [
        { index: true, element: <OverviewPage /> },
        { path: 'users', element: <UsersPage /> },
        { path: 'billing', element: <BillingPage /> },
        { path: 'quality', element: <QualityPage /> },
        { path: 'monitoring', element: <Navigate to="/billing" replace /> },
        { path: 'reconciliation', element: <Navigate to="/billing" replace /> },
        { path: 'system', element: <SystemManagementPage /> },
      ],
    }],
  },
], { basename: '/admin' })
