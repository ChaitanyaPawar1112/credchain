# CredChain – Frontend

The web app for **CredChain – Blockchain-Based Academic Credential Verification System**.
One React app serves all four kinds of users, plus a public page anyone can use to check a certificate.

| Who | Where | What they do |
|-----|-------|--------------|
| Anyone (no login) | `/verify` | Check a certificate by QR link, hash, by uploading the PDF, or by uploading a photo or scan (AI forgery check) |
| Anyone with a share link | `/s/<link>` | See the one certificate a student shared with them, and download its PDF |
| College (no login) | `/apply` | Apply to issue certificates on CredChain |
| Super admin | `/admin` | Approve or reject colleges, see users, compare the database with the blockchain, read the verification log, see each college's AI trust score, analytics and the audit log |
| College admin | `/institution` | Add or import students, give claim codes, create batches, issue them on the blockchain, download PDFs, revoke, see the college's analytics and audit log |
| Student | `/student` | Connect their account with a claim code, then download and share their certificates, and manage private share links |
| Employer / verifier | `/verifier` | A shortcut to the verify page after logging in |

Stack: Vite, React 19, React Router 7, TanStack Query 5, Tailwind CSS 4, TypeScript, Vitest with Testing Library.

## Run it on your computer

You need **Node.js 20.19+ or 22.12+** and the CredChain backend running on `http://localhost:8080`.

1. Start what the backend needs:
   - PostgreSQL (your local installation).
   - Docker Desktop, then the file storage used for certificate PDFs and the AI fraud-detection service:
     ```
     cd infra
     docker compose up -d
     ```
     This reads `infra/.env` (git-ignored, never commit it). Without the fraud service the photo check and
     the trust scores say the service is not available; everything else works. The same command starts
     **Mailpit**, a test mail server: every email CredChain sends appears at **http://localhost:8025**.
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
| `npm test` | All tests, once. On a slow Windows laptop use `npx vitest run --pool=threads --maxWorkers=1` if it says "Timeout waiting for worker" |
| `npm run typecheck` | TypeScript check only |
| `npm run build` | Typecheck, then a production build into `dist/` |
| `npm run preview` | Serve the production build locally |

### Settings

| Variable | Default | Meaning |
|----------|---------|---------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Where the backend is. Set it in `frontend/.env.local` (git-ignored) if yours runs elsewhere. |

The links inside QR codes and share buttons come from the backend's `VERIFICATION_BASE_URL`
(default `http://localhost:5173/verify`), private share links from `SHARE_BASE_URL`
(default `http://localhost:5173/s`) and the links inside emails from `WEB_BASE_URL`
(default `http://localhost:5173`). Change them together when deploying.

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
7. **Photo or scan.** On **Verify**, open the **Photo or scan** tab and upload a screenshot or phone photo of a
   certificate (JPEG, PNG, WebP or PDF, up to 5 MB). You get a risk score (low, medium, high), what the QR code points
   to, each printed detail compared with the official record, every finding with its points, and for JPEG photos an
   edit heat map (Error Level Analysis). Change the CGPA in Paint and check it again: that detail shows
   *Not found on the copy* and the score goes up.
8. **Trust scores.** As super admin, open **College trust scores** and click **Run now** (it also runs every night at
   02:30). Each college gets 0 to 100 (*Trusted* 80+, *Watch* 50+, *At risk* below 50) with the reasons. The
   **Verification log** shows photo checks as *Photo or scan* with their risk score.
9. **Notifications.** The bell at the top right shows a red count of unread notifications and checks for new ones
   every 30 seconds. A student hears when a certificate is issued, revoked or checked as genuine; a college admin when
   a batch is on the blockchain or someone checks a fake copy of one of its certificates; the super admin when a college
   drops to *At risk*. Students and colleges also get emails (approval, rejection, certificate issued, revoked):
   open **http://localhost:8025** to read them.
10. **Analytics.** Super admin or college admin, **Analytics**: totals, a daily chart of certificates and checks for
    the last 7, 30 or 90 days (hover a day, or click **Show as table**), checks by result and method, and for the
    super admin the most active colleges. A college admin only ever sees its own numbers.
11. **Share links.** Student, **My certificates → Share → Create a private link**: give it a name (for example the
    employer) and how long it works (7 days to 1 year). The link opens `/s/...`, which shows the live check of that
    certificate and its PDF. **Share links** lists every link with how often it was opened; **Turn off** stops one at once.
12. **Audit log.** Super admin or college admin, **Audit log**: who logged in or failed to, which colleges were
    reviewed, students added, batches issued, certificates revoked and share links made, with a filter per area.
    It cannot be edited or deleted, not even in the database.

## Good to know

- **"Your blockchain wallet is not ready yet."** Issuing needs the college's wallet to be active. It is activated
  automatically after approval when the backend's blockchain settings are filled in; the college overview shows its status.
- **No PDF button.** PDFs are created a little after a batch is recorded on the blockchain, and only when the backend's
  file storage is switched on. Until then the student sees "PDF is being prepared".
- **Logins.** The access token is kept in memory only; the refresh token is kept in the browser so a reload keeps you
  logged in. Changing your password logs you out everywhere.
- **Privacy.** The public verify page never shows a student's email, the certificate's salt or a college's internal notes.
- **A photo is never proof.** The photo check only says how suspicious a copy looks. Only the QR link, the hash or the
  original PDF prove a certificate is genuine. See `fraud-service/README.md` for how the score is made.

## Code layout

```
src/
  api/          one file per backend area (auth, verify, admin, institution, student, notifications,
                analytics, audit, share) on top of client.ts
  auth/         login state, token refresh, RequireAuth (role-based routes)
  components/   shared building blocks: Button, TextField, Modal, Panel, Badge, StatTile, ...
    charts/       DailyBars and BarList (plain CSS, no chart library)
  layouts/      public layout, dashboard layout with the sidebar and notification bell, navigation per role
  pages/
    verify/       public verify page (hash, PDF, photo or scan)
    admin/        super admin pages
    institution/  college admin pages
    student/      student pages (certificates, share links)
    AnalyticsPage, AuditLogPage, SharedCertificatePage (public /s/:token)
  lib/format.ts dates, short hashes, "3 minutes ago"
```

Tests sit next to the pages they cover (`*.test.tsx`). They replace `fetch` with a small table of
expected requests, so they run without the backend.
