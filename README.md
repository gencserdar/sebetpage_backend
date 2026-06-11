# Social Platform - Microservice System

This directory contains the Spring Boot microservice version of the social/chat
application. The browser talks to one public edge service, `api-gateway`, over
HTTP and WebSocket/STOMP. The gateway talks to the domain services over gRPC on
the private Docker network, with an internal metadata token on each RPC and
env-driven TLS/mTLS settings for hardened deployments.

```text
Browser / React app
   |
   | HTTP API + SockJS/STOMP
   v
+-----------------------------+
| api-gateway                 |
| REST, CORS, Security, WS    |
+-------------+---------------+
              |
              | private gRPC + internal token
              | TLS/mTLS when GRPC_* envs enable it
              |
   +----------+----------+-----------+-----------+
   v                     v           v           v
+--------------+  +--------------+  +----------+  +--------------+
| auth-service |  | user-service |  | group    |  | chat-service |
| credentials  |  | profiles     |  | service  |  | messages/ws  |
+------+-------+  +------+-------+  +----+-----+  +------+-------+
       |                 |               |               |
       v                 v               v               v
   auth_db           user_db         group_db         chat_db

RabbitMQ is used for async mail jobs, consumed by mail-worker. Chat-service can
also publish committed chat events through RabbitMQ so WebSocket fan-out keeps
working when chat-service runs more than one instance.
```

## Services

| Service | Responsibility | Public from host? |
| --- | --- | --- |
| `api-gateway` | REST API, CORS, JWT auth filter, refresh-cookie endpoints, WebSocket/STOMP bridge | Yes, `GATEWAY_PORT` |
| `auth-service` | Credentials, password hashing, JWT issuing/validation, activation, password reset, refresh-token storage | No, Docker network only |
| `user-service` | Profiles, friend requests, friendships, blocks, search, S3 profile photos | No, Docker network only |
| `community-service` | Post communities (subreddit-style), members, invites | No, Docker network only |
| `chat-service` | Conversations, AES-GCM encrypted messages, unread/read state, presence, chat events | No, Docker network only |
| `mail-worker` | Consumes RabbitMQ mail jobs and sends SMTP email | No |

Each domain service has its own MySQL database. Cross-service references use
plain user ids; there are no foreign keys across service databases.

## Repository Layout

```text
microservices/
  pom.xml                 parent Maven build
  proto-contracts/        protobuf contracts and generated gRPC stubs
  common-lib/             shared ServiceException, gRPC error mapping/auth, JWT/AES helpers
  api-gateway/            public edge service
  auth-service/           credentials and session source of truth
  user-service/           profile/friend/block/search domain
  community-service/      post communities (not chat groups)
  chat-service/           chat, read state, presence, events
  mail-worker/            async email worker
  docker-compose.yml      local orchestration
```

## API And Auth Flow

The frontend keeps access tokens in memory only. Refresh tokens are stored in an
HttpOnly cookie named `refreshToken`.

1. `POST /api/auth/login` sends credentials to the gateway.
2. The gateway calls `auth-service.Login`.
3. `auth-service` returns a short-lived access token and a refresh token.
4. The gateway returns the access token in JSON and writes the refresh token as
   an HttpOnly cookie.
5. Normal API calls send `Authorization: Bearer <accessToken>`.
6. If the access token expires, the frontend calls `POST /api/auth/refresh`.
7. The gateway reads the refresh cookie, calls `auth-service.Refresh`, rotates
   the refresh token, and returns a fresh access token.

Important token rules:

- Access tokens carry `uid` and subject email. They do not carry `type`.
- Refresh tokens carry `type=refresh`.
- `auth-service.ValidateToken` rejects tokens with a `type` claim, so refresh
  tokens cannot be used as bearer access tokens.
- `auth-service.Refresh` requires `type=refresh`, verifies the JWT signature,
  checks expiry, and matches the submitted refresh token against the bcrypt hash
  stored in the credentials table.
- Logout clears the browser cookie and revokes the stored refresh token. If the
  frontend has already dropped the access token, the gateway identifies the user
  from the refresh cookie before revoking.

## Gateway Security

`api-gateway` is the only service intended to be reachable by the browser.

- CORS origins come from `ALLOWED_ORIGINS`.
- CSRF is disabled because the API uses bearer access tokens for protected
  endpoints. Refresh/logout still rely on the HttpOnly cookie, so keep
  `SameSite=Lax` and strict CORS origins.
- `/api/auth/**`, `/ws/**`, and `/actuator/**` are unauthenticated at the Spring
  Security route layer. Auth-specific handlers validate what they need
  explicitly, and WebSocket handshakes verify a short-lived ticket before
  accepting.
- Rate limiting is applied to login, register, forgot-password, reset-password,
  activation, OTP-issuing endpoints, group-chat write/photo actions, and chat
  sends. STOMP chat sends use the same Redis token-bucket infrastructure.
- Rate limiting uses the direct remote address by default. Set
  `TRUST_PROXY_HEADERS=true` only behind a trusted proxy that strips spoofed
  forwarding headers and sets the real client IP.
- Internal service HTTP/gRPC ports are declared with `expose`, not `ports`, in
  `docker-compose.yml`, so they are reachable by other containers but not
  published directly to the host. The gateway is the public edge.
- Internal gRPC clients send `x-internal-grpc-token`; gRPC servers reject calls
  without the matching `INTERNAL_GRPC_TOKEN` value.
- Internal gRPC transport is controlled by `GRPC_CLIENT_NEGOTIATION_TYPE` and
  `GRPC_SERVER_SECURITY_*`. Local dev can use `PLAINTEXT`; production should use
  `TLS` plus server certificates, and `client-auth=REQUIRE` when mTLS is used.

## Password And Account Changes

- Registration creates credentials in `auth-service`, then mirrors the profile
  row in `user-service`.
- Account activation uses an emailed activation link.
- Forgot-password sends a reset link to the registered email. Reset links expire
  after `RESET_CODE_TTL_MINUTES` minutes and are invalidated after
  `RESET_CODE_MAX_ATTEMPTS` bad verifier attempts.
- Password reset sends the reset code in the query string and the new password
  in the JSON body:

```http
POST /api/auth/reset-password?code=<reset-selector>.<reset-verifier>
Content-Type: application/json

{ "newPassword": "..." }
```

Successful password reset clears existing refresh-token state, so other logged-in
sessions have to authenticate again.

- Logged-in password changes use a two-step flow:
  - request with current password and new password
  - confirm with a 6-digit code sent to the current email
- Logged-in email changes use a two-step flow:
  - request new email
  - confirm with a 6-digit code sent to the new email

## WebSocket / Chat Flow

Before connecting, the browser asks the gateway for a short-lived WebSocket
ticket with authenticated REST:

```text
POST /api/ws-ticket
```

Then SockJS/STOMP uses that ticket in the handshake:

```text
/ws?ticket=<ws-ticket>
```

SockJS does not reliably support custom Authorization headers, so the real
access token is never placed in the WebSocket URL. The gateway verifies the
ticket before accepting the connection. Tickets are short-lived, single-use, and
bound to the requesting client address/user-agent pair. Tickets are stored in
Redis with TTL and consumed with an atomic get-and-delete operation, so the
single-use guarantee still holds across gateway instances.

After STOMP connect:

1. The gateway opens `chat-service.SubscribeEvents(userId)`.
2. `chat-service` keeps a per-user stream in its local event broker.
3. Chat events from gRPC are forwarded to user-scoped STOMP queues. When
   `CHAT_EVENTS_RABBIT_ENABLED=true`, committed events are also published to a
   RabbitMQ fanout exchange so other chat-service instances can deliver them to
   their own connected WebSocket users.
4. On disconnect, the gRPC stream is cancelled and presence is broadcast.

Current user queues include:

- `/user/queue/friends` for friend events, presence snapshots/updates, and user
  update events
- `/user/queue/unread` for unread-count changes
- `/user/queue/messages/{conversationId}` for conversation messages

Chat security notes:

- The gateway derives the sender from the authenticated WebSocket principal.
- `chat-service` verifies the sender is an active participant before accepting a
  message.
- Direct messages are blocked if either participant has blocked the other.
- Messaging-group sends are always delivered. Only the blocker hides the
  blocked user's messages; the blocked user still sees the blocker's messages
  and sees the blocker as offline. Group settings list users you blocked;
  users who blocked you appear offline.
- Messaging groups enforce env-driven limits for max members, title length,
  description length, and message length.
- Group photo changes go through the upload endpoint and user-service image
  validation. The generic group update endpoint does not accept arbitrary
  `imageUrl` updates.
- Messages are encrypted at rest with AES-256-GCM using `CHAT_AES_KEY_BASE64`.

## Docker Compose

Start the stack:

```bash
cd microservices
docker compose up --build
```

Compose starts:

- one MySQL database per service
- RabbitMQ with management UI bound to localhost only
- `auth-service`, `user-service`, `community-service`, `chat-service`
- `mail-worker`
- `api-gateway`

Only the gateway is intended as the application entrypoint:

```text
http://localhost:<GATEWAY_PORT>
```

The MySQL ports are bound to `127.0.0.1` for local development convenience. Do
not publish database ports publicly in production.

## Frontend Integration

The React app should point at the gateway:

```env
REACT_APP_GATEWAY_BASE_URL=http://localhost:<GATEWAY_PORT>
```

If that variable is not set, the frontend falls back to `REACT_APP_API_BASE_URL`
and then `window.location.origin`.

## Required Environment Variables

Use `.env` for local compose. Real secrets must not be committed.

| Variable | Used by | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | auth-service | Base64 HMAC key for JWT signing |
| `JWT_EXPIRATION` | auth-service | Access-token TTL in milliseconds |
| `REFRESH_EXPIRATION_DEFAULT` | auth-service | Default refresh-cookie age in seconds |
| `REFRESH_EXPIRATION_REMEMBER` | auth-service | Remember-me refresh-cookie age in seconds |
| `CHAT_AES_KEY_BASE64` | chat-service | Base64 256-bit AES-GCM key |
| `INTERNAL_GRPC_TOKEN` | all gRPC services | Shared internal RPC authentication token |
| `ALLOWED_ORIGINS` | api-gateway | Comma-separated browser origins |
| `TRUST_PROXY_HEADERS` | api-gateway | Set to `true` only behind a trusted proxy that strips spoofed forwarding headers |
| `WS_TICKET_TTL_SECONDS` | api-gateway | WebSocket ticket TTL in seconds |
| `GRPC_CLIENT_NEGOTIATION_TYPE` | gRPC clients | `PLAINTEXT` for local dev, `TLS` for encrypted internal RPC |
| `GRPC_SERVER_SECURITY_ENABLED` | gRPC servers | Enables server-side TLS when `true` |
| `GRPC_SERVER_SECURITY_CLIENT_AUTH` | gRPC servers | `NONE`, `OPTIONAL`, or `REQUIRE`; use `REQUIRE` for mTLS |
| `RATE_LIMIT_LOGIN_CAPACITY`, `RATE_LIMIT_LOGIN_WINDOW_SECONDS` | api-gateway | Login attempts per client IP/window |
| `RATE_LIMIT_REGISTER_CAPACITY`, `RATE_LIMIT_REGISTER_WINDOW_SECONDS` | api-gateway | Registration attempts per client IP/window |
| `RATE_LIMIT_FORGOT_PASSWORD_CAPACITY`, `RATE_LIMIT_FORGOT_PASSWORD_WINDOW_SECONDS` | api-gateway | Forgot-password requests per client IP/window |
| `RATE_LIMIT_REQUEST_EMAIL_CHANGE_CAPACITY`, `RATE_LIMIT_REQUEST_EMAIL_CHANGE_WINDOW_SECONDS` | api-gateway | Email-change code requests per client IP/window |
| `RATE_LIMIT_REQUEST_PASSWORD_CHANGE_CAPACITY`, `RATE_LIMIT_REQUEST_PASSWORD_CHANGE_WINDOW_SECONDS` | api-gateway | Password-change code requests per client IP/window |
| `RATE_LIMIT_ACTIVATE_CAPACITY`, `RATE_LIMIT_ACTIVATE_WINDOW_SECONDS` | api-gateway | Activation attempts per client IP/window |
| `RATE_LIMIT_RESET_PASSWORD_CAPACITY`, `RATE_LIMIT_RESET_PASSWORD_WINDOW_SECONDS` | api-gateway | Reset-password attempts per client IP/window |
| `RATE_LIMIT_MESSAGING_GROUP_WRITE_CAPACITY`, `RATE_LIMIT_MESSAGING_GROUP_WRITE_WINDOW_SECONDS` | api-gateway | Messaging-group create/update/member/delete per client IP/window |
| `RATE_LIMIT_MESSAGING_GROUP_PHOTO_CAPACITY`, `RATE_LIMIT_MESSAGING_GROUP_PHOTO_WINDOW_SECONDS` | api-gateway | Messaging-group photo uploads per client IP/window |
| `RATE_LIMIT_CHAT_SEND_CAPACITY`, `RATE_LIMIT_CHAT_SEND_WINDOW_SECONDS` | api-gateway | REST/STOMP chat sends per client/window |
| `MESSAGING_GROUP_MAX_MEMBERS` | chat-service | Maximum active members in a messaging group, creator included |
| `MESSAGING_GROUP_MAX_TITLE_CHARS` | chat-service | Maximum group title length |
| `MESSAGING_GROUP_MAX_DESCRIPTION_CHARS` | chat-service | Maximum group description length |
| `CHAT_MESSAGE_MAX_CHARS` | chat-service | Maximum plaintext message length before encryption |
| `CHAT_EVENTS_RABBIT_ENABLED` | chat-service | Enables RabbitMQ fan-out for chat events across chat-service instances |
| `RESET_CODE_TTL_MINUTES` | auth-service | Forgot-password reset-link TTL in minutes |
| `RESET_CODE_MAX_ATTEMPTS` | auth-service | Bad verifier attempts before a reset link is invalidated |
| `ACCOUNT_CHANGE_CODE_TTL_MINUTES` | auth-service | Logged-in email/password change code TTL in minutes |
| `ACCOUNT_CHANGE_CODE_MAX_ATTEMPTS` | auth-service | Bad code attempts before a pending email/password change is invalidated |
| `FRONTEND_BASE_URL` | auth-service | Password-reset email links |
| `GATEWAY_BASE_URL` | auth-service | Activation email links |
| `RABBITMQ_USER`, `RABBITMQ_PASS` | RabbitMQ/services | Broker credentials |
| `MAIL_HOST`, `MAIL_PORT`, SMTP vars | mail-worker | Email delivery |
| AWS S3 vars | user-service | Profile photo storage |

## Running Without Docker

Install shared modules first:

```bash
mvn -pl proto-contracts,common-lib -am install -DskipTests
```

Then run services in separate terminals with the required environment variables:

```bash
mvn -pl auth-service spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl community-service spring-boot:run
mvn -pl chat-service spring-boot:run
mvn -pl api-gateway spring-boot:run
mvn -pl mail-worker spring-boot:run
```

You also need MySQL schemas for `auth_db`, `user_db`, `group_db`, and `chat_db`,
plus RabbitMQ.

## Build And Verification

Build all backend modules:

```bash
mvn -q -DskipTests package
```

Build the frontend from `../frontend`:

```bash
npm run build
```

Health endpoints are available through each service HTTP port inside the Docker
network, and through whatever endpoints are intentionally exposed at the gateway.

## Production Hardening Notes

These are known follow-ups before a real production deployment:

- Replace `hibernate.ddl-auto=update` with Flyway or Liquibase migrations.
- Enable TLS/mTLS for internal gRPC outside local dev. Set
  `GRPC_CLIENT_NEGOTIATION_TYPE=TLS`, `GRPC_SERVER_SECURITY_ENABLED=true`, and
  `GRPC_SERVER_SECURITY_CLIENT_AUTH=REQUIRE`, then provide the certificate/key
  resources through the net.devh gRPC Spring properties
  (`GRPC_SERVER_SECURITY_CERTIFICATE_CHAIN`,
  `GRPC_SERVER_SECURITY_PRIVATE_KEY`,
  `GRPC_SERVER_SECURITY_TRUST_CERT_COLLECTION`,
  `GRPC_CLIENT_GLOBAL_SECURITY_TRUST_CERT_COLLECTION`,
  `GRPC_CLIENT_GLOBAL_SECURITY_CLIENT_AUTH_ENABLED`,
  `GRPC_CLIENT_GLOBAL_SECURITY_CERTIFICATE_CHAIN`,
  `GRPC_CLIENT_GLOBAL_SECURITY_PRIVATE_KEY`).
- Leave `TRUST_PROXY_HEADERS=false` unless a trusted reverse proxy strips
  untrusted `X-Forwarded-For` values and sets the real client IP.
- Do not publish database ports publicly.
