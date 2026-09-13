import { apiClient } from './apiClient'

/**
 * POST /api/auth/signup  { name, email, password, confirmPassword } -> 201
 * POST /api/auth/login   { email, password } -> LoginResponse (token + profile)
 * GET  /api/auth/me      -> AuthenticatedUser (requires a valid session token)
 * POST /api/auth/logout  -> void (stateless JWT: purely a client-side token discard)
 */
export interface AuthenticatedUser {
  id: number
  name: string
  email: string
}

export interface SignUpRequest {
  name: string
  email: string
  password: string
  confirmPassword: string
}

export interface SignInRequest {
  email: string
  password: string
}

export interface LoginResponse extends AuthenticatedUser {
  token: string
}

export const authApi = {
  async signUp(request: SignUpRequest): Promise<void> {
    await apiClient.post('/api/auth/signup', request)
  },

  async signIn(request: SignInRequest): Promise<LoginResponse> {
    const { data } = await apiClient.post<LoginResponse>('/api/auth/login', request)
    return data
  },

  async me(): Promise<AuthenticatedUser> {
    const { data } = await apiClient.get<AuthenticatedUser>('/api/auth/me')
    return data
  },

  async logout(): Promise<void> {
    await apiClient.post('/api/auth/logout')
  },
}
