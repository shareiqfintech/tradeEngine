# Trading Engine Console (frontend)

A standalone React + TypeScript operations console for the
TradingView → Spring Boot → Groww automated F&O trading engine
(`../trading-webhook-engine`). This app is built and deployed independently
of the backend - it talks to it only over HTTP, never calls Groww directly,
and never touches MySQL/Redis directly.

```
React (this app) → Spring Boot (trading-webhook-engine) → Groww
```

## 1. Stack

React 19 · TypeScript (strict) · Vite · Tailwind CSS v4 · Radix UI primitives
(hand-built into a small shadcn-style component library under
`src/components/ui`, not the shadcn CLI) · React Router v7 · TanStack Query v5
· Recharts · Axios · Lucide icons.

## 2. Running locally

```bash
npm install
cp .env.example .env.local   # then edit VITE_API_BASE_URL if needed
npm run dev
```

Frontend: http://localhost:5173. It expects the backend at
http://localhost:8080 by default (see `.env.example`).

`npm run dev`'s Vite server proxies `/api/*` and `/actuator/*` to
`VITE_API_BASE_URL` (see `vite.config.ts`), so the browser only ever talks to
`localhost:5173` - no CORS configuration is needed on the Spring Boot side
for local development.

### Environment variables

| Variable | Used by | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | Vite dev proxy target, Nginx `BACKEND_UPSTREAM` at build/run time | Where the real backend lives. Leave the in-browser behavior on relative `/api` paths (see `src/api/apiClient.ts`) - this variable configures the *proxy*, not a URL baked into API calls, unless explicitly set to a non-empty absolute URL. |

No other environment variables exist. **Nothing else is configurable from
the frontend** - Groww credentials, database credentials, and the webhook
secret live only in the backend's environment.

## 3. Building

```bash
npm run build   # tsc -b (strict typecheck) && vite build
npm run lint    # eslint .
```

Both are verified clean as of this build: `tsc -b` reports zero errors under
strict settings (`noUnusedLocals`, `noUnusedParameters`, `verbatimModuleSyntax`,
`erasableSyntaxOnly`), and `eslint .` reports zero errors (a few
`react-refresh/only-export-components` warnings on files that intentionally
export both a component and small constants/hooks - harmless, standard for
this pattern).

The build is code-split by route (`React.lazy` in `src/app/router.tsx`) plus
manual vendor chunks (`react`, `@tanstack/react-query`, `recharts`,
`@radix-ui/*`) configured in `vite.config.ts`, so no single chunk exceeds
Vite's size warning threshold.

## 4. Docker

```bash
docker build -t trading-frontend .
docker run -d -p 8080:80 \
  -e BACKEND_UPSTREAM=backend:8080 \
  trading-frontend
```

Multi-stage build: stage 1 (`node:22-alpine`) runs `npm ci && npm run build`;
stage 2 (`nginx:1.27-alpine`) serves only the static `dist/` output - there
is no Node process running in the production image.

**`BACKEND_UPSTREAM`** (default `backend:8080`, matching a docker-compose
service literally named `backend`) is templated into the Nginx config at
*container start* via the official Nginx image's `envsubst`-on-templates
mechanism - change it with `docker run -e` / `docker-compose.yml`
`environment:`, no image rebuild required.

This was actually built and run during development, including a full
end-to-end pass proxying real requests through Nginx to a real running
`trading-webhook-engine.jar` (`GET /api/trading/status`, `GET /actuator/health`,
`POST /api/trading/pause` all verified working through the container). Two
real bugs were found and fixed in the process, both worth knowing about if
you touch `nginx.conf`:

1. **A static `proxy_pass http://backend:8080;` crashes Nginx at container
   startup** if `backend` isn't resolvable yet (e.g. testing the image
   standalone, or if the backend container starts slightly later). Fixed by
   resolving the upstream **lazily, per-request**, via a `resolver` directive
   + a variable in `proxy_pass` (`proxy_pass http://$backend_upstream...;`).
   This requires Docker's embedded DNS at `127.0.0.11`, which is only
   available on **user-defined** networks (exactly what `docker compose`
   creates) - not the default bridge network. Test with `docker network create`
   / `docker compose`, not a bare `docker run` with no `--network`.
2. **Once a variable is used in `proxy_pass`, Nginx no longer does its usual
   "strip the matched location prefix" rewriting** - the URI you write after
   the host is sent verbatim, silently dropping the rest of the original
   path. Since this app's routes are proxied 1:1 (`/api/... -> /api/...`,
   no prefix stripping needed), the fix is `proxy_pass http://$backend_upstream$request_uri;`.

### Full local stack (frontend + backend + MySQL + Redis)

This repository's frontend Dockerfile/`nginx.conf` don't include a
docker-compose file that also builds the backend - the backend already has
its own `docker-compose.yml` (`../trading-webhook-engine/docker-compose.yml`,
MySQL + Redis) and this project intentionally does not modify backend files.
To run the full stack together, either:

- Run the backend normally (`java -jar` or your own container) plus this
  image on the same Docker network, with `BACKEND_UPSTREAM` pointing at it, or
- Add a `backend` service to a compose file of your own that builds
  `../trading-webhook-engine` and a `frontend` service that builds this
  directory, on one shared network - `BACKEND_UPSTREAM=backend:8080` then
  works out of the box since Docker Compose's embedded DNS resolves service
  names automatically.

## 5. Production deployment (AWS EC2, per project architecture)

```
AWS EC2
├── Docker
│   ├── Spring Boot        (trading-webhook-engine)
│   ├── MySQL
│   ├── Redis
│   └── React/Nginx        (this image)
└── AWS Secrets Manager    (Groww API key/TOTP secret, DB/Redis passwords, webhook secret)
```

This frontend image never talks to MySQL/Redis directly and never needs any
of the secrets above - it only needs to reach the backend's HTTP port on the
Docker network (`BACKEND_UPSTREAM`). If MySQL/Redis later move to RDS/ElastiCache,
nothing here changes.

## 6. API contract assumptions

**Reused as-is from the real backend** (`com.example.trading.controller.TradingAdminController`):

| Method | Path | Notes |
|---|---|---|
| GET | `/api/trading/status` | `TradingStatusResponse` - polled every 5s |
| POST | `/api/trading/pause` | |
| POST | `/api/trading/resume` | |
| POST | `/api/trading/kill-switch/enable` | requires confirmation dialog in the UI |
| POST | `/api/trading/kill-switch/disable` | |
| GET | `/actuator/health` | Spring Boot Actuator, already exposed via `management.endpoints.web.exposure.include: health,info`. **Caveat:** without `management.endpoint.health.show-details: always` (or `when-authorized`) set on the backend, this returns only `{"status":"UP"}` with no per-component (DB/Redis) breakdown. The Health page shows "NOT EXPOSED" for those rows until that one line is added to the backend's `application.yml` - it never fabricates a DB/Redis status. |

**NOT yet implemented on the backend** - the frontend calls these exact
paths (see `src/api/*.ts`, each documented at the top of its file) and
renders an honest "not available yet" state (never mock data) when they
404/501/error:

| Method | Path | Backing data (real vs. not) |
|---|---|---|
| GET | `/api/trading/signals` | `trading_signal` table is real; this read endpoint is not |
| GET | `/api/trading/signals/{signalId}` | same, plus its audit trail |
| GET | `/api/trading/orders` | `orders` table is real; this read endpoint is not |
| GET | `/api/trading/orders/{id}` | same |
| GET | `/api/trading/positions` | `GrowwPositionService.getPositions()` is real and already called server-side (to compute `openPositions` in the status response) - just never exposed over HTTP |
| GET | `/api/trading/risk` | Risk *limits* are real config (`TradingProperties.Risk`); current-usage counters are computed per-signal internally but never returned as a standalone snapshot |
| GET | `/api/trading/audit` | `audit_event` table is real and already populated by `AuditService` on every event; just never exposed over HTTP. Also used, filtered by `eventType=RISK_REJECTED`, as the Risk page's rejection history |
| GET | `/api/trading/config` | Mirrors `TradingProperties` (minus the `groww` block, which must never be returned) |
| GET | `/api/trading/pnl/summary` | **No backend source at all today** - `daily_trading_summary.realized_pnl` exists as a column but nothing computes a non-zero value into it; unrealized P&L and win/loss have no source whatsoever |
| GET | `/api/trading/pnl/intraday` | Same - no backend source |
| GET | `/api/health/summary` | Last-Groww-auth/last-webhook/last-order/uptime have no backend source |
| POST/GET | `/api/auth/*` | The backend has **no user-authentication system** at all yet - see §7 |

## 7. Authentication

The backend has no login/session concept today - only a server-to-server
shared secret on the TradingView webhook, which a browser user never sees or
sends. `src/api/authApi.ts` defines the interface this app is ready to use
the moment that changes; `src/app/AuthProvider.tsx` is a deliberately honest
pass-through (every visitor is treated as a single "operator" identity) - it
does **not** simulate a working login form that "succeeds" against nothing.
`/login` exists as a real route and explains this rather than hiding it.

Nothing sensitive is ever stored in `localStorage`/`sessionStorage` - there
is nothing to store yet, and when real auth lands it should use an
HttpOnly cookie set by the backend, not client-side storage.

## 8. Security notes

- The Groww API key, TOTP secret, Groww access token, database
  username/password, Redis password, and TradingView webhook secret are
  never sent to, stored in, or rendered by this frontend. The Settings page
  explicitly does not request or display a `groww` config block even in its
  expected API contract (see `src/types/settings.ts`).
- All backend calls go through one Axios instance (`src/api/apiClient.ts`)
  with normalized error handling (`src/api/errors.ts`) - no component talks
  to `fetch`/`axios` directly.
- Kill switch and LIVE-mode-adjacent actions use a shared
  `ConfirmationDialog` (`src/components/shared/ConfirmationDialog.tsx`) that
  supports a "type to confirm" gate. There is currently no backend endpoint
  to switch `trading.mode` to LIVE at runtime - by backend design, that
  requires a config change and restart (see the backend's own README) - so
  this frontend does not offer a LIVE-mode switch at all; it only ever
  displays whichever mode `GET /api/trading/status` reports.

## 9. Real-time updates

The backend does not currently expose a WebSocket or SSE endpoint. Every
page uses TanStack Query polling instead, tuned per the data's volatility:

- Trading status, signals, orders, positions, risk: every 5s
- System health, activity/audit feed: every 10s
- Settings/config: fetched once (effectively static at runtime)

Polling automatically pauses while the browser tab is in the background
(TanStack Query's default `refetchIntervalInBackground: false`) and backs
off on repeated 4xx errors (see `src/app/queryClient.ts`) instead of
hammering a misconfigured backend.

## 10. Project structure

```
src/
  api/         Axios instance + one module per resource (tradingApi, signalApi, ...)
  app/         Router, QueryClient, AuthProvider, ErrorBoundary, App entry wiring
  components/
    ui/        Hand-built Radix-based primitives (button, card, table, dialog, ...)
    status/    Domain badges (TradingModeBadge, GrowwStatusBadge, ActionBadge, ...)
    layout/    Sidebar, TopBar, AppLayout, MobileNav
    dashboard/ TradingControls, PnlChart, status-label derivation
    signals/ orders/ positions/ risk/ activity/ health/   feature components per page
    shared/    PageHeader, EmptyState, ErrorState, ConfirmationDialog, skeletons, Pagination
  hooks/       One TanStack Query hook module per resource + useToast/useDebouncedValue/useIndiaClock
  lib/         cn(), format.ts, marketHours.ts (display-only), optionSymbol.ts (display-only parse), queryParams.ts
  pages/       One page component per route
  types/       domain.ts (enum-shaped unions), dto.ts (backend response shapes, real vs. expected), settings.ts
```

## 11. What this frontend deliberately does NOT do

Per the project's trading-safety rules, no trading decision, risk
calculation, order-quantity decision, option-contract resolution, or
BUY/SELL strategy logic lives in this codebase. The two places that parse
domain data client-side (`src/lib/marketHours.ts`, `src/lib/optionSymbol.ts`)
are explicitly documented as **display-only** - a market-hours badge and a
cosmetic trading-symbol label split, respectively - and are never consulted
before sending a control command. Every button that could affect trading
(pause/resume/kill-switch) does nothing but call the corresponding real
backend endpoint and render whatever it reports back.
