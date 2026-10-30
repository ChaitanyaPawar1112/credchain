# CredChain security notes

This document lists how CredChain protects its users and their data, and what the Phase 9 security review
(October 2026) checked and changed. Report a security problem privately to the maintainers, not in a public issue.

## How the app is protected

| Area | Protection |
|------|------------|
| Passwords | Stored only as BCrypt hashes. The password policy (`@StrongPassword`) is checked on sign-up and on every change. College admins must replace their temporary password at the first login. |
| Password guessing | An account locks for 15 minutes after 5 wrong passwords. On top of that, each visitor (IP address) gets at most 10 logins, sign-ups or college applications per minute. |
| Sessions | Short-lived access tokens (15 minutes) are kept only in memory in the browser. Refresh tokens are rotated on every use, and only their SHA-256 hash is stored. Re-using an old refresh token logs that user out everywhere, and so does changing the password. |
| Who may do what | Every API call checks the user's role (super admin, college admin, student, verifier). College admins only ever see and change their own college's data, and students only their own certificates. |
| Public pages | Verification never shows a student's email, a certificate's salt or a college's internal notes. Lookups (30 per minute) and uploads (10 per minute) are limited per visitor. The visitor's address comes from our own nginx, which overwrites any `X-Forwarded-For` the browser sends, so the limit cannot be dodged with a fake header. |
| Share links | Each share link is 32 random bytes (43 characters), so links cannot be guessed. An expired, turned-off and never-existing link all get the same answer. |
| Uploads | At most 5 MB. A PDF must start with `%PDF-`. Photos are only read by the AI service and never stored. |
| Blockchain keys | Each college wallet's private key is encrypted with AES-256-GCM (`WALLET_ENCRYPTION_KEY`) and tied to that college's id. The platform admin key is only in `infra/.env`. |
| Audit log | Logins, failed logins, college reviews, students, batches, revocations and share links are recorded. A database trigger refuses any change or deletion of the log. |
| Secrets | Everything secret lives in `infra/.env`, which git ignores. The repository only contains test-only keys used by the automated tests, each marked as such. |
| Website | Strict security headers on every page (see below), with no third-party scripts at all. |
| Production settings | The `prod` profile turns off Swagger and the API docs, hides error details and stack traces, and makes `/actuator/health` say only UP or DOWN. Without the database, JWT and CORS settings the app refuses to start. |
| Docker | The backend and AI service run as normal users, not root, and nginx serves pages from unprivileged worker processes. Only the website's port is reachable from other computers. The database has no open port at all, and the helpers (file storage, AI service, Mailpit) only listen on `127.0.0.1`. |
| Automatic checks | Every pull request runs the backend, website, AI service and smart contract tests and builds the Docker images on GitHub (`.github/workflows/ci.yml`). The workflow has read-only access and no secrets. |

## Website security headers

Set by nginx (`frontend/security-headers.conf`):

- `Content-Security-Policy`: scripts only from our own address; styles and fonts also from Google Fonts; images may
  also be `data:` and `blob:` (QR codes, the photo-check heat map); API calls only to our own address; no plugins;
  no framing.
- `X-Frame-Options: DENY`: no other site can show CredChain in a frame (click-jacking).
- `X-Content-Type-Options: nosniff`: browsers must not guess file types.
- `Referrer-Policy: strict-origin-when-cross-origin`: other sites never see a full share link.
- `Permissions-Policy`: no camera, microphone or location.

HTTPS and `Strict-Transport-Security` are added where the app is put online (step 9.4), in front of nginx.

## What the October 2026 review changed

1. **Login and sign-up limit per visitor.** Before, only the account lockout slowed password guessing, so someone
   could try one password against many accounts. Now login, sign-up and college applications share a limit of
   10 per minute per visitor (`app.public-rate-limit.sign-ins-per-minute`).
2. **Security headers** on the website, as listed above.
3. **Helper ports closed to other computers.** File storage, the AI service and Mailpit now listen only on `127.0.0.1`.
4. **Settings template cleaned up.** `infra/.env.example` lost settings nothing reads any more (Redis, RabbitMQ,
   MinIO, old blockchain names). It gained the ones that were missing (`FRAUD_API_KEY`, the link addresses) and
   explains how to generate each secret.

Checked and found fine: no SQL is built from user input (all queries use parameters); no passwords, tokens or keys
are written to the logs; `npm audit` of the website finds 0 known vulnerabilities; self sign-up can only create
student or verifier accounts.

## Known limits

- The rate limit counts in the backend's memory. That is right for one backend instance; several instances
  would need a shared counter (for example Redis).
- The refresh token is kept in the browser's local storage so a reload keeps you logged in. The strict
  Content-Security-Policy is what protects it from injected scripts.
- Sepolia is a test network. A real launch would use a hardware wallet or multisig as the platform admin.
