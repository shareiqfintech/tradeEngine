# trading-webhook-engine — Phase 2 (working trading engine)

Automated TradingView → Groww F&O trading backend.

```
TradingView Pine Script → TradingView Alert → HTTPS Webhook
    → auth filter → validate → duplicate check (Redis) → save (MySQL) → 202
    → async: TradingEngineService
        → market hours → trading state → Groww auth → distributed lock
        → BUY: option contract resolution → risk → PAPER/LIVE order
        → SELL: find open long position → risk → PAPER/LIVE order (same contract)
    → order status → audit trail
```

Phase 1 was the webhook intake surface only. **Phase 2 (this drop) is a
functioning engine**: real Groww TOTP authentication, real Groww REST calls
(orders/positions/instrument master), Redis-backed dedup/locking, MySQL
persistence via Flyway, risk management, option contract resolution, BUY/SELL
execution, order-status reconciliation, and operator controls (kill switch /
pause / resume / status). Nothing here is a stub - every class listed below
has a real implementation, and `mvn clean test` is green (103 tests) plus a
full live smoke test against real MySQL/Redis containers (see §11).

## 1. Files created / modified in Phase 2

```
config/
  TradingProperties.java       - typed binding of the whole trading.* config tree
  AppConfig.java                - @EnableAsync/@EnableScheduling, tradingExecutor bean
  RedisConfig.java               - StringRedisTemplate + atomic unlock Lua script
  WebClientConfig.java           - two WebClient beans (Groww API, instrument CSV asset)

entity/ + repository/           - TradingSignalEntity, OrderEntity, AuditEventEntity,
                                   PositionSnapshotEntity, DailyTradingSummaryEntity
                                   + one Spring Data repository per entity
db/migration/V1__init_schema.sql - Flyway migration creating all 5 tables

groww/
  TotpGenerator.java              - RFC 6238 TOTP, hand-rolled HMAC-SHA1 + Base32 (no 3rd-party lib)
  GrowwTokenManager.java          - in-memory token/expiry/state holder
  GrowwAuthenticationService.java - the TOTP auth flow, lock-guarded against concurrent auth
  GrowwApiClient.java             - the ONLY component that calls api.groww.in
  dto/                            - GrowwApiResponse envelope, token/order/position DTOs

redis/
  SignalDeduplicationService.java - trading:signal:{signalId}, TTL 24h
  DistributedLockService.java     - trading:order-lock:{underlying}, atomic release

service/
  SignalValidationService, TradingStateService, RiskManagementService,
  PositionService, GrowwPositionService, AuditService, MarketHoursService,
  InstrumentMasterService, OptionContractResolver, OrderReferenceGenerator,
  GrowwOrderStatusService, TradingEngineService

scheduler/
  GrowwAuthenticationScheduler  - 08:00 IST Mon-Fri + bounded retries
  TradingWindowScheduler        - session lifecycle: 09:25 activate, 15:10 SAFETY EXIT
                                  (stop new entries + auto-close system-managed positions),
                                  15:30 official close; 60s monitor + on-startup recompute
  OrderReconciliationScheduler  - polls order status + position mismatch during market hours

trading session (Asia/Kolkata, see TradingSessionService / SessionCloseService):
  09:25:00              session opens - NEW ENTRIES ALLOWED
  09:25:00 - 15:09:59   normal trading
  15:10:00              SAFETY EXIT / trading cutoff (NOT the market close):
                          - new entries blocked (SESSION_TRADING_CUTOFF)
                          - every SYSTEM-MANAGED open position auto-closed using the
                            ACTUAL broker quantity (never configured lots); manual Groww
                            positions are never touched
  15:10:00 - 15:29:59   exit / reconciliation only, no new entries
  15:30:00              official NSE close - MARKET_CLOSED; the JAR keeps running 24/7
  states: PRE_MARKET -> TRADING_ACTIVE -> SESSION_CLOSING -> SAFETY_EXIT_COMPLETED -> MARKET_CLOSED
  Every new entry also re-asserts the current IST time immediately before the order is
  dispatched (assertNewEntryAllowed), so a signal delayed past 15:10 in the async queue
  still never opens a position.

controller/
  TradingViewWebhookController.java (rewritten) - now persists/dedupes/dispatches
  TradingAdminController.java (new)             - kill-switch, pause/resume, status

exception/ (new)  - InvalidSignalException, DuplicateSignalException, MarketClosedException,
                     AuthenticationRequiredException, TokenExpiredException, RiskRejectedException,
                     PositionNotFoundException, ContractNotFoundException, GrowwApiException,
                     OrderRejectedException  (+ GlobalExceptionHandler updated)

docker-compose.yml (new) - local MySQL 8 + Redis 7 for development

src/test/java/... (new)  - 103 tests across every layer (see §12)
```

## 2. Environment variables

| Variable | Required | Purpose |
|---|---|---|
| `TRADINGVIEW_WEBHOOK_SECRET` | yes | Shared secret TradingView must send as `X-TradingView-Secret` |
| `JWT_SECRET` | yes | HMAC-SHA signing key (>= 32 bytes) for user session JWTs |
| `CREDENTIAL_ENCRYPTION_KEY` | yes | AES-256 key (base64, 32 bytes) encrypting each user's Groww API key/TOTP secret at rest |
| `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` | yes | MySQL connection (`DB_PORT` defaults to 3306) |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | yes | Redis connection |

`trading.mode` (PAPER default / LIVE) is set in `application.yml`, **not** an
env var - it is deliberately not something you can flip without a config
change + restart.

**There is no `GROWW_API_KEY`/`GROWW_TOTP_SECRET` env var.** A Groww API
key/TOTP secret belongs to a specific application user, not the
application itself - each user signs up, signs in, and enters their own
credentials on the Groww Settings page, where they're stored encrypted
(see §7).

## 3. How to run MySQL

```bash
cd trading-webhook-engine
docker compose up -d mysql
```

This starts MySQL 8 on `localhost:3306` with database `trading` (root
password from `DB_PASSWORD`, default `root`). If port 3306 is already used
by a local MySQL install (common on dev machines), set `DB_PORT=3307` (or
any free port) before both `docker compose up` and running the app - the
compose file and `application.yml` both honor it:

```bash
DB_PORT=3307 docker compose up -d mysql
export DB_PORT=3307
```

Flyway applies `V1__init_schema.sql` automatically on application startup -
no manual schema step.

## 4. How to run Redis

```bash
docker compose up -d redis
```

Starts Redis 7 on `localhost:6379`, no password by default.

## 5. How to run Spring Boot

```bash
mvn clean package -DskipTests
export TRADINGVIEW_WEBHOOK_SECRET=dev-secret-change-me
export JWT_SECRET=dev-only-jwt-signing-secret-must-be-at-least-32-bytes-long
export CREDENTIAL_ENCRYPTION_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=
export DB_HOST=localhost DB_PORT=3306 DB_USERNAME=root DB_PASSWORD=root
export REDIS_HOST=localhost REDIS_PORT=6379
java -jar target/trading-webhook-engine.jar
```

Groww credentials are NOT set here - sign up, sign in, and enter them on
the Groww Settings page (see §7).

`trading.mode` is `PAPER` by default in `application.yml` - **leave it there**
until you have explicitly verified LIVE behaviour is what you want (see §10).

## 6. How to test the TradingView webhook

```bash
curl -i -X POST http://localhost:8080/api/webhook/tradingview \
  -H "Content-Type: application/json" \
  -H "X-TradingView-Secret: dev-secret-change-me" \
  -d '{
    "signalId": "NIFTY-001",
    "action": "BUY",
    "underlying": "NIFTY",
    "exchange": "NSE",
    "timeframe": "15m",
    "price": 25000.50,
    "timestamp": "2026-09-08T09:30:00+05:30"
  }'
```

Expect `202 {"status":"ACCEPTED","signalId":"NIFTY-001"}` immediately. What
happens next (visible in the logs and in `trading_signal`/`orders`/`audit_event`)
depends on context:

- Before 09:25 IST Mon-Fri (or weekend) → `REJECTED`, reason `SESSION_NOT_STARTED`
  (rejected at intake, never dispatched).
- At/after 15:10 IST → `REJECTED`, reason `SESSION_TRADING_CUTOFF` (the safety exit;
  the market itself is open until 15:30 but this app opens no new positions).
- Inside 09:25-15:10 but Groww not authenticated → `REJECTED`, reason `AUTH_REQUIRED`.
- Inside 09:25-15:10, authenticated, PAPER mode → `EXECUTED`, a `PAPER_ORDER`
  audit event, and an `orders` row with status `COMPLETE` - **no call to Groww is made**.
- Send the exact same `signalId` again within 24h → still `202`, but no new
  row is inserted and the engine is never invoked a second time (`DUPLICATE_SIGNAL` audit event only).

This was verified with a real end-to-end run against Dockerized MySQL/Redis
during development (see §11).

## 7. How to configure your Groww API key + TOTP secret (per user)

A Groww API key/TOTP secret is **per-user data**, not application
configuration - there is no env var for it. Each application user
configures their own:

1. Sign up (`POST /api/auth/signup`) and sign in (`POST /api/auth/login`)
   - or use the Sign Up / Sign In pages in the frontend.
2. Log in to Groww → Cloud API page → generate an API key, and separately
   choose "Generate TOTP Token" to get the **Secret** (a Base32 string, the
   same kind of secret Google Authenticator/Authy would scan from a QR
   code - not the 6-digit token itself).
3. On the Groww Settings page (or `PUT /api/settings/groww`, authenticated
   with your session JWT), paste both values and Save. They are encrypted
   (`CredentialEncryptionService`, AES-256-GCM) and stored in
   `groww_configuration`, one row per user - never logged, never returned
   in full by any GET.
4. Click "Test Groww Connection" (`POST /api/settings/groww/test-connection`)
   to verify them against the real Groww API. Only a successful test marks
   the account `connected` - saving new credentials always resets
   `connected` to false until re-verified.

## 8. How Groww authentication works (per user)

Implemented in `GrowwAuthenticationService` + `GrowwTokenManager` +
`TotpGenerator` + `GrowwApiClient` - the same single implementation for
every user, parameterized by `userId`:

1. `TotpGenerator.currentCode(totpSecret)` computes the current 6-digit RFC
   6238 TOTP code (HMAC-SHA1, 30s step) directly from the JDK's `javax.crypto`
   - no third-party TOTP library.
2. `GrowwAuthenticationService.authenticate(userId)` loads that user's own
   decrypted API key/TOTP secret (`GrowwSettingsService`), then
   `GrowwApiClient.requestAccessToken(apiKey, totp)` calls
   `POST https://api.groww.in/v1/token/api/access` with
   `Authorization: Bearer <that user's API key>`.
3. On success, the access token is stored **only in memory**, keyed by
   `userId`, inside `GrowwTokenManager` (a `Map<Long, TokenState>`, not a
   single shared field) - never logged, never printed, never returned by
   any endpoint, never written to disk. One user's token can never be read
   as, or overwrite, another's.
4. `authenticate(userId)` is guarded by a per-user `ReentrantLock`, so
   concurrent requests for the SAME user share one Groww call; different
   users never block each other.
5. If Groww ever responds 401/403 to a *later* API call (order/positions),
   `handleAuthFailureFromBroker(userId)` marks that user's token
   `TOKEN_EXPIRED` (and their `connected` flag false) - no further live
   orders are attempted for them until re-authentication succeeds.
6. The webhook-triggered automated engine has no logged-in session of its
   own (TradingView authenticates via shared secret, not a user login), and
   a TradingView alert does not belong to any one application user either -
   it is one common signal (e.g. "BUY NIFTY") executed independently for
   **every** user who currently has a verified (`connected=true`)
   configuration, via `GrowwUserResolver.findAllConnectedUserIds()` - never
   routed to just one of them, and never picked by "most recently
   connected". `GrowwAuthenticationScheduler` authenticates every connected
   user this way automatically at **08:00 IST, Monday-Friday**, with a small
   number of spaced 5-minute retries per user if the first attempt fails,
   so each has succeeded (or given up) well before the 09:15 trading window.

`TradingEngineService` never places an order for a user unless
`GrowwAuthenticationService.isAuthenticated(userId) == true` for that
specific user - every Groww API call (positions, orders, order status,
live-data) takes an explicit `userId` and resolves that user's own session
token fresh, every time.

## 9. How to verify authentication without placing an order

Authentication and order placement are completely separate code paths -
`GrowwAuthenticationService` never calls `order/create`, and nothing about
placing a PAPER or LIVE order triggers a fresh authentication attempt beyond
what's already cached. To check auth status only:

```bash
curl -s http://localhost:8080/api/trading/status -H "Authorization: Bearer <your session JWT>"
```

```json
{"mode":"PAPER","tradingEnabled":false,"growwAuthenticated":true,"killSwitch":false,"paused":false,"ordersToday":0,"openPositions":0}
```

`growwAuthenticated` reflects the CALLING user's own `GrowwTokenManager`
state (requires a valid session JWT - this endpoint is per-user, not
global). This endpoint **never** exposes the API key, TOTP secret, or
access token - verified by a dedicated test
(`TradingAdminControllerTest.status_neverExposesCredentials`).

To force an authentication attempt right now for your own account (e.g. to
test your API key/TOTP secret before market hours) without waiting for the
08:00 scheduler, either click "Test Groww Connection" on the Groww Settings
page, or:

```bash
curl -s -X POST http://localhost:8080/api/trading/groww/authenticate -H "Authorization: Bearer <your session JWT>"
```

## 10. PAPER vs LIVE mode

`trading.mode: PAPER` in `application.yml` is the default and is what ships.
In PAPER mode, `TradingEngineService` runs every check (market hours,
trading state, auth, position, contract resolution, risk) exactly as LIVE
would, but `executeOrder()` calls `simulatePaperOrder()` instead of
`GrowwApiClient.createOrder()` - **verified by `GrowwApiClient` never being
invoked** in the PAPER-mode tests and in the live smoke test (§11). The
simulated order is saved to `orders` with status `COMPLETE` and a
`PAPER_ORDER` audit event is recorded.

Switching to LIVE means changing `trading.mode: LIVE` in `application.yml`
and restarting - it is not an environment variable or a runtime toggle, on
purpose.

## 11. Live smoke test performed during development

This was actually run, not just described, back when Groww credentials
were still a single global `GROWW_API_KEY`/`GROWW_TOTP_SECRET` env var
(now per-user - see §7): `docker compose up -d` (MySQL + Redis),
`java -jar target/trading-webhook-engine.jar` with those set to
placeholder values (no real Groww account touched), then:

- Sent a real BUY webhook → `202` → confirmed in MySQL that `trading_signal`
  got a real row, `audit_event` got `SIGNAL_RECEIVED`/`SIGNAL_SAVED` rows,
  and (since it was sent outside market hours) the async engine correctly
  rejected it with `MARKET_CLOSED` and recorded that too - the Flyway schema,
  entities, and full webhook→engine pipeline all really worked end-to-end.
- Re-sent the identical `signalId` → still `202`, but Redis correctly
  short-circuited it (`DUPLICATE_SIGNAL` audit event, no second DB row, no
  second engine invocation).
- Exercised `kill-switch/enable`, `kill-switch/disable`, `pause`, `resume`,
  and `status` against the running app.
- Confirmed a wrong/missing webhook secret gets `401` and an unsupported
  exchange gets `400` with a clear message.

## 12. Tests

`mvn clean test` → **103 tests, 0 failures** across:

- `TotpGeneratorTest` - RFC 6238 conformance vector + property tests
- `GrowwTokenManagerTest`, `GrowwAuthenticationServiceTest` - auth success/failure/expiry/concurrency-safety
- `GrowwApiClientTest` - real HTTP round-trips against a local `HttpServer` (envelope parsing, 401 auth-error mapping, header construction) - no mocked WebClient internals
- `SignalDeduplicationServiceTest`, `DistributedLockServiceTest` - Redis behaviour (mocked `StringRedisTemplate`)
- `RiskManagementServiceTest` - every rejection reason + approval path
- `OptionContractResolverTest`, `InstrumentMasterServiceTest` - ATM/ITM/OTM strike math, expiry selection, CSV parsing
- `GrowwPositionServiceTest`, `PositionServiceTest` - position mapping, NO_LONG_POSITION rule
- `GrowwOrderStatusServiceTest` - Groww status string → `OrderStatus` mapping
- `TradingEngineServiceTest` - the full BUY/SELL pipeline: PAPER execution, LIVE order submission, LIVE order rejection, risk rejection, market-closed, kill-switch, auth-required, SELL-with-position, **SELL-without-position (never calls Groww)**, distributed-lock contention, duplicate short-circuit
- `TradingViewWebhookControllerTest`, `TradingAdminControllerTest` - MockMvc slices, including "status never leaks credentials"
- `RepositoryIntegrationTest` - runs the real Flyway migration against H2-in-MySQL-mode and exercises all 5 repositories
- `SharedSecretWebhookAuthenticatorTest` (Phase 1, unchanged)

No test hits the real Groww API or requires a running MySQL/Redis - only the
one-off manual smoke test in §11 did.

## 13. What's deliberately out of scope in this phase

Short-selling (`trading.allow-short-selling=true`) is recognized in config
but not implemented - SELL always means "close an existing long", never
"open a short", per the spec. Order modify/cancel endpoints exist in
Groww's API but aren't wired up (not required by the spec). A manual
"authenticate now" admin endpoint was intentionally not added (§9).
