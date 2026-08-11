import { createContext, useContext } from 'react'
import type { AdminMe, LoginRequest } from './authApi'

export interface AuthContextValue {
  administrator: AdminMe | null
  pending: boolean
  error: boolean
  login: (request: LoginRequest) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
