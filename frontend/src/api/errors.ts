import { AxiosError } from 'axios'
import type { ApiErrorBody } from '@/types'

export type ApiErrorKind =
  | 'validation'
  | 'unauthorized'
  | 'forbidden'
  | 'not_found'
  | 'conflict'
  | 'rate_limited'
  | 'server_error'
  | 'network'
  | 'not_implemented'
  | 'unknown'

/**
 * Normalized error shape every API call in this app rejects with. UI code
 * never touches raw Axios/HTTP internals - it only ever sees this, so
 * error handling (toasts, inline messages) is consistent everywhere.
 */
export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status: number | null
  readonly fieldErrors?: Record<string, string>

  constructor(message: string, kind: ApiErrorKind, status: number | null, fieldErrors?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

function kindForStatus(status: number): ApiErrorKind {
  switch (status) {
    case 400:
      return 'validation'
    case 401:
      return 'unauthorized'
    case 403:
      return 'forbidden'
    case 404:
      return 'not_found'
    case 409:
      return 'conflict'
    case 429:
      return 'rate_limited'
    case 501:
      return 'not_implemented'
    default:
      return status >= 500 ? 'server_error' : 'unknown'
  }
}

const FRIENDLY_MESSAGES: Record<ApiErrorKind, string> = {
  validation: 'The request was rejected as invalid.',
  unauthorized: 'You are not signed in, or your session has expired.',
  forbidden: 'You do not have permission to perform this action.',
  not_found: 'The requested resource was not found.',
  conflict: 'This action conflicts with the current state and could not be completed.',
  rate_limited: 'Too many requests. Please wait a moment and try again.',
  server_error: 'The trading engine encountered an internal error.',
  network: 'Trading engine is unavailable. Check that the backend is running and reachable.',
  not_implemented: 'This feature is not yet available on the trading engine backend.',
  unknown: 'An unexpected error occurred.',
}

/** Converts any thrown value from an Axios call into a normalized {@link ApiError}. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  if (error instanceof AxiosError) {
    if (error.response) {
      const body = error.response.data as ApiErrorBody | undefined
      const status = error.response.status
      const kind = kindForStatus(status)
      const message = body?.message?.trim() ? body.message : FRIENDLY_MESSAGES[kind]
      return new ApiError(message, kind, status, body?.fieldErrors)
    }
    if (error.code === 'ECONNABORTED' || error.message.toLowerCase().includes('timeout')) {
      return new ApiError('The request to the trading engine timed out.', 'network', null)
    }
    return new ApiError(FRIENDLY_MESSAGES.network, 'network', null)
  }

  if (error instanceof Error) {
    return new ApiError(error.message || FRIENDLY_MESSAGES.unknown, 'unknown', null)
  }

  return new ApiError(FRIENDLY_MESSAGES.unknown, 'unknown', null)
}

export function friendlyMessageFor(kind: ApiErrorKind): string {
  return FRIENDLY_MESSAGES[kind]
}
