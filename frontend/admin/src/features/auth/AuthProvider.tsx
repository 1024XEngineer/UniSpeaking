import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { getCurrentAdministrator, loginAdministrator, logoutAdministrator } from './authApi'
import type { LoginRequest } from './authApi'
import { AuthContext } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const current = useQuery({
    queryKey: ['administrator', 'me'],
    queryFn: getCurrentAdministrator,
    retry: false,
    staleTime: 30_000,
  })

  async function login(request: LoginRequest) {
    await loginAdministrator(request)
    await current.refetch()
  }

  async function logout() {
    await logoutAdministrator()
    queryClient.setQueryData(['administrator', 'me'], null)
  }

  return (
    <AuthContext.Provider value={{
      administrator: current.data ?? null,
      pending: current.isPending,
      error: current.isError,
      login,
      logout,
    }}>
      {children}
    </AuthContext.Provider>
  )
}
