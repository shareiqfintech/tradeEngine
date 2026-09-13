import axios from 'axios'
import { toApiError } from './errors'
import { getAuthToken } from '@/lib/authToken'

/**
 * Single Axios instance every API module goes through.
 *
 * Base URL strategy: `VITE_API_BASE_URL` is intentionally NOT read here to
 * build an absolute URL by default. Every call site uses a path relative to
 * the current origin (e.g. `/api/trading/status`), which:
 *   - in `npm run dev`, is transparently proxied to the backend by Vite
 *     (see vite.config.ts `server.proxy`, itself driven by VITE_API_BASE_URL)
 *   - in production, is transparently proxied to the backend by Nginx
 *     (see nginx.conf), inside the same Docker network
 *
 * This means the browser never needs the Spring Boot backend to send CORS
 * headers, in either environment. If `VITE_API_BASE_URL` is explicitly set
 * to a non-empty absolute URL, it IS honored (for the rare case of pointing
 * a deployed frontend at a backend on a different origin that has its own
 * CORS configuration) - see the fallback below.
 */
const explicitBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

export const apiClient = axios.create({
  baseURL: explicitBaseUrl && explicitBaseUrl.length > 0 ? explicitBaseUrl : '',
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = getAuthToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(toApiError(error)),
)
