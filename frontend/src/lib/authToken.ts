const STORAGE_KEY = 'trading-engine-auth-token'

/**
 * Single source of truth for where the session JWT lives, so
 * `apiClient.ts` (attaches it to outgoing requests) and `AuthProvider.tsx`
 * (owns auth state) never need to import from each other.
 */
export function getAuthToken(): string | null {
  return localStorage.getItem(STORAGE_KEY)
}

export function setAuthToken(token: string): void {
  localStorage.setItem(STORAGE_KEY, token)
}

export function clearAuthToken(): void {
  localStorage.removeItem(STORAGE_KEY)
}
