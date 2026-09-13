import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, type AuthenticatedUser } from '@/api/authApi'
import { getAuthToken, setAuthToken, clearAuthToken } from '@/lib/authToken'

interface AuthContextValue {
  user: AuthenticatedUser | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (token: string, user: AuthenticatedUser) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * On mount, if a token is already stored, validates it against
 * GET /api/auth/me before trusting it - an expired/invalid token never
 * silently renders the app as authenticated. `login` is called once, right
 * after a successful POST /api/auth/login, with the token and profile the
 * backend just returned.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  // Lazy-initialized from whether a token exists at all, so the "no token"
  // case never needs a synchronous setState inside the effect below - only
  // the async me() round trip (when a token IS present) settles isLoading,
  // from its own .finally() callback rather than the effect body itself.
  const [isLoading, setIsLoading] = useState(() => getAuthToken() !== null)

  useEffect(() => {
    if (!getAuthToken()) {
      return
    }
    authApi
      .me()
      .then(setUser)
      .catch(() => {
        clearAuthToken()
        setUser(null)
      })
      .finally(() => setIsLoading(false))
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isLoading,
      login: (token, authenticatedUser) => {
        setAuthToken(token)
        setUser(authenticatedUser)
      },
      logout: () => {
        authApi.logout().catch(() => {
          // Stateless JWT: there is nothing more to do server-side even if this call fails - proceed with the local logout regardless.
        })
        clearAuthToken()
        setUser(null)
      },
    }),
    [user, isLoading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}
