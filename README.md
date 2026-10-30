# CredChain

**Blockchain-Based Academic Credential Verification System.** Colleges issue tamper-proof certificates, students
share them, and anyone can check one in seconds, without trusting the college, the student or the CredChain server.

[![CI](https://github.com/ChaitanyaPawar1112/credchain/actions/workflows/ci.yml/badge.svg)](https://github.com/ChaitanyaPawar1112/credchain/actions/workflows/ci.yml)

## How it works

1. A college applies, and the super admin approves it. The college gets its own blockchain wallet.
2. The college adds its students (one by one or from a CSV file) and issues certificates in batches. Each batch
   is one transaction on Ethereum (Sepolia): a Merkle root of the certificates' keccak256 fingerprints, sent from
   the college's wallet to the `CredentialRegistry` contract.
3. Every certificate becomes a PDF with a QR code. The student connects their account with a claim code from the
   college, downloads the PDF and shares it as a link, a QR code or a private share link.
4. An employer scans the QR code, pastes the link or uploads the PDF. CredChain recomputes the fingerprint and
   checks it against the database and, live, against the blockchain. A changed PDF fails. A revoked certificate
   says so.
5. For copies that can't be checked byte for byte (phone photos, scans), an AI service reads the QR code and the
   printed text and gives a **risk score** with reasons. A machine-learning model watches every college's
   activity for unusual days.

No personal data is ever written on the blockchain: only fingerprints, which cannot be turned back into names.

## What's inside

| Folder | What | Stack |
|--------|------|-------|
| [`backend/`](backend) | The API: logins and roles, colleges, students, certificates, PDFs, blockchain worker, verification, notifications, analytics, audit log, share links | Java 21, Spring Boot 4.1, PostgreSQL 17, Flyway, web3j, OpenPDF |
| [`frontend/`](frontend/README.md) | The website for all users, plus the public verify page | React 19, TypeScript, Vite, Tailwind CSS 4, TanStack Query |
| [`fraud-service/`](fraud-service/README.md) | AI checks of photos and scans; trust score per college | Python 3.12, FastAPI, OpenCV, Tesseract OCR, scikit-learn |
| [`contracts/`](contracts/README.md) | The `CredentialRegistry` smart contract, live and verified on Sepolia at [`0x1EA8…47b0`](https://sepolia.etherscan.io/address/0x1EA82E244e3b38Fc294075Ab03D30F5e3E2947b0#code) | Solidity 0.8.34, OpenZeppelin 5, Hardhat 3 |
| [`infra/`](infra) | Docker Compose for the helpers or the whole app, the HTTPS proxy, and the settings template | Docker, nginx, Caddy, RustFS, Mailpit |

```
browser ──▶ nginx (website + /api/) ──▶ backend ──▶ PostgreSQL
                                           ├──▶ file storage (certificate PDFs)
                                           ├──▶ AI service (photos, trust scores)
                                           ├──▶ mail server
                                           └──▶ Ethereum Sepolia (CredentialRegistry)
```

## Who uses it

| Who | What they do |
|-----|--------------|
| Super admin | Approves colleges, sees all users, compares the database with the blockchain, reads the verification and audit logs, sees analytics and each college's trust score |
| College admin | Adds or imports students, issues certificate batches on the blockchain, downloads PDFs, revokes, sees its own analytics and audit log |
| Student | Connects their record with a claim code, downloads certificates, shares them and manages private share links |
| Employer / anyone | Verifies a certificate by QR code, link, PDF upload or photo, without logging in |

## Run it

The quickest way is everything in Docker. You need Docker Desktop.

1. Copy `infra/.env.example` to `infra/.env` and fill in your own values. At least set `DB_PASSWORD`, `JWT_SECRET`,
   `SUPER_ADMIN_*`, `S3_SECRET_KEY` and `WALLET_ENCRYPTION_KEY`. `infra/.env` is git-ignored: never commit it.
2. Start everything:
   ```
   cd infra
   docker compose --profile app up -d --build
   ```
3. Open **http://localhost:8088** and log in with `SUPER_ADMIN_EMAIL` and `SUPER_ADMIN_PASSWORD`. Every email the
   app sends shows up in Mailpit at http://localhost:8025.

The blockchain stays off until you set `BLOCKCHAIN_ENABLED=true` with a Sepolia RPC URL and the platform admin key.

**To develop:** run only the helpers with `docker compose up -d`, start the backend in IntelliJ (`BackendApplication`,
or `./mvnw spring-boot:run` in `backend`) and the website with `npm run dev` in `frontend`. The website is then at
http://localhost:5173. Details: [`frontend/README.md`](frontend/README.md).

**To put it online** with HTTPS on a cloud server, follow [`infra/DEPLOY.md`](infra/DEPLOY.md).

## Tests

Every pull request runs all of these on GitHub ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)):

| Part | Command | Tests |
|------|---------|-------|
| Backend | `./mvnw test` in `backend` (needs PostgreSQL with a `credchain_test` database) | 226 |
| Website | `npm test` in `frontend` | 63 |
| AI service | `docker build --target test fraud-service` | 29 |
| Smart contract | `npx hardhat test` in `contracts` | 21 |

The Docker images for the backend, website and AI service are built on every pull request too.

## Security

Passwords are hashed with BCrypt, and accounts lock after repeated wrong passwords. Each visitor gets limited
logins and checks. Every role sees only its own data. Wallet keys are encrypted, the audit log cannot be changed,
the website sends strict security headers, and online everything runs behind HTTPS. Details and known limits:
[`SECURITY.md`](SECURITY.md).

## Project

Student project, built in nine phases (2026): authentication and roles, college onboarding, smart contract,
issuance pipeline, public verification, website, AI fraud detection, extras (notifications, analytics, audit log,
share links), and production polish (Docker, automatic tests, security review, deployment).
