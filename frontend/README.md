# CredChain – Frontend

The web app for **CredChain – Blockchain-Based Academic Credential Verification System**.
One React app serves all four kinds of users, plus a public page anyone can use to check a certificate.

| Who | Where | What they do |
|-----|-------|--------------|
| Anyone (no login) | `/verify` | Check a certificate by QR link, hash, or by uploading the PDF |
| College (no login) | `/apply` | Apply to issue certificates on CredChain |
| Super admin | `/admin` | Approve or reject colleges, see users, compare the database with the blockchain, read the verification log |
| College admin | `/institution` | Add or import students, give claim codes, create batches, issue them on the blockchain, download PDFs, revoke |
| Student | `/student` | Connect their account with a claim code, then download and share their certificates |
| Employer / verifier | `/verifier` | A shortcut to the verify page after logging in |

Stack: Vite, React 19, React Router 7, TanStack Query 5, Tailwind CSS 4, TypeScript, Vitest with Testing Library.

## Run it on your computer

You need **Node.js 20.19+ or 22.12+** and the CredChain backend running on `http://localhost:8080`.

1. Start what the backend needs:
   - PostgreSQL (your local installation).
   - Docker Desktop, then the file storage used for certificate PDFs:
     ```
     cd infra
     docker compose up -d
     ```
     This reads `infra/.env` (git-ignored, never commit it).
2. Run the backend (in IntelliJ, or `./mvnw spring-boot:run` in `backend`) and wait until it says it started.
3. Start the frontend:
   ```
   cd frontend
   npm install
   npm run dev
   ```
4. Open **http://localhost:5173**. Use `localhost`, not `127.0.0.1`: the backend only accepts requests from
   `http://localhost:5173` (its `CORS_ALLOWED_ORIGINS` setting).

| Command | What it does |
|---------|--------------|
| `npm run dev` | Development server with live reload on port 5173 |
| `npm test` | All tests, once |
| `npm run typecheck` | TypeScript check only |
| `npm run build` | Typecheck, then a production build into `dist/` |
| `npm run preview` | Serve the production build locally |

### Settings

| Variable | Default | Meaning |
|----------|---------|---------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Where the backend is. Set it in `frontend/.env.local` (git-ignored) if yours runs elsewhere. |

The links inside QR codes and share buttons come from the backend's `VERIFICATION_BASE_URL`
(default `http://localhost:5173/verify`). Change both together when deploying.

## Try the whole flow

1. **Super admin.** Log in with the super admin account from the backend's settings
   (`SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD`).
2. **A college applies.** Log out, open **Apply as a college** (`/apply`) and submit the form.
3. **Approve it.** As super admin, open **College applications**, open the college and click **Approve**.
   Copy the temporary password shown once. The college's blockchain wallet is created and activated in the background.
4. **College admin.** Log in with the contact person's email and the temporary password. You must choose a new password first.
   - **Students:** add one, or **Import CSV** (download the template first). The file is checked before anything is saved.
   - **Batches → New batch → Add certificates:** pick the students, enter CGPA and grade, then **Issue on blockchain**.
     The page moves through *Waiting to send → Confirming → Issued* by itself and then links to Etherscan.
   - **Certificates:** download the PDF (it has the QR code), open the public verify page, or **Revoke** with a reason.
   - **Students → Get claim code** for the student who will log in next.
5. **Student.** Sign up as a student, enter the college code, enrollment number and claim code, then open
   **My certificates** to download or **Share** (link, QR code, WhatsApp, LinkedIn, email).
6. **Anyone.** Open the shared link, or go to **Verify** and upload the PDF. Change one character in the PDF and upload
   it again: the result is *not a genuine CredChain certificate*.

## Good to know

- **"Your blockchain wallet is not ready yet."** Issuing needs the college's wallet to be active. It is activated
  automatically after approval when the backend's blockchain settings are filled in; the college overview shows its status.
- **No PDF button.** PDFs are created a little after a batch is recorded on the blockchain, and only when the backend's
  file storage is switched on. Until then the student sees "PDF is being prepared".
- **Logins.** The access token is kept in memory only; the refresh token is kept in the browser so a reload keeps you
  logged in. Changing your password logs you out everywhere.
- **Privacy.** The public verify page never shows a student's email, the certificate's salt or a college's internal notes.

## Code layout

```
src/
  api/          one file per backend area (auth, verify, admin, institution, student) on top of client.ts
  auth/         login state, token refresh, RequireAuth (role-based routes)
  components/   shared building blocks: Button, TextField, Modal, Panel, Badge, StatTile, ...
  layouts/      public layout, dashboard layout with the sidebar, navigation per role
  pages/
    verify/       public verify page
    admin/        super admin pages
    institution/  college admin pages
    student/      student pages
  lib/format.ts dates, short hashes, "3 minutes ago"
```

Tests sit next to the pages they cover (`*.test.tsx`). They replace `fetch` with a small table of
expected requests, so they run without the backend.
