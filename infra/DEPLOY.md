# Put CredChain online

This guide runs the whole app on one cloud server with Docker, behind HTTPS, so anyone can open it from a
phone or another computer. It is the same setup as `docker compose --profile app` on your laptop, plus Caddy,
which gets a free HTTPS certificate by itself.

```
visitor ──HTTPS──▶ Caddy (proxy) ──▶ nginx (web) ──/api/──▶ backend ──▶ PostgreSQL (db)
                                                         ├──▶ file storage (RustFS)
                                                         ├──▶ AI service (fraud)
                                                         ├──▶ Mailpit (mail)
                                                         └──▶ Sepolia (Alchemy)
```

Only ports 80 and 443 are open to the internet. Everything else stays inside Docker or on the server itself.

## What you need

- **A Linux server** (Ubuntu 24.04) with at least **2 CPUs, 4 GB memory and 30 GB disk**. Any provider works.
  Two that can cost nothing for a student project:
  - **Azure for Students**: credit without a credit card. A `B2s` virtual machine (2 CPUs, 4 GB) is enough.
  - **Oracle Cloud Free Tier**: an "Ampere A1" virtual machine (ARM, up to 4 CPUs and 24 GB) is free forever.
    It asks for a card to verify you. All CredChain images also build on ARM.
- **A web address.** The simplest is free and needs no account: take the server's public IP address, replace
  the dots with dashes, and add `.sslip.io`. For `20.198.5.17` that is `20-198-5-17.sslip.io`. You can use your
  own domain instead: point an `A` record at the server's IP.
- Your own values for everything in `infra/.env`. New secrets for the server are better than copying the ones
  from your laptop.

## 1. Create the server

In your cloud provider's portal, create an Ubuntu 24.04 virtual machine with the size above. Choose to log in
with an SSH key and download the private key file.

Then open these ports to the internet in the provider's network or firewall settings: **22** (SSH, ideally only
from your own IP), **80** and **443** (TCP), and **443 UDP** (optional, faster HTTP/3).

Note the server's **public IP address**.

## 2. Log in to the server

On Windows, in PowerShell, from the folder with the key file:

```
ssh -i .\your-key.pem azureuser@20.198.5.17
```

The user name depends on the provider: `azureuser` on Azure, `ubuntu` on Oracle. Type `yes` the first time.
Every command below runs **on the server**.

## 3. Install Docker

```
curl -fsSL https://get.docker.com | sudo sh
```

```
sudo usermod -aG docker $USER
```

Log out (`exit`) and log in again with the same `ssh` command, then check:

```
docker compose version
```

Expected: `Docker Compose version v2...`.

On **Oracle Cloud**, Ubuntu images also block ports in the server's own firewall. Open 80 and 443 there too:

```
sudo iptables -I INPUT 6 -p tcp -m multiport --dports 80,443 -j ACCEPT
```

```
sudo netfilter-persistent save
```

## 4. Get the code

```
git clone https://github.com/ChaitanyaPawar1112/credchain.git
```

If the repository is private, Git asks for a user name and password. Use your GitHub user name. For the
password, use a token from GitHub → Settings → Developer settings → Personal access tokens → Fine-grained token,
with read access to this repository only.

```
cd credchain/infra
```

## 5. Write the settings

```
cp .env.example .env
```

```
nano .env
```

Fill in every value. These ones matter most online:

| Setting | Value on the server |
|---------|---------------------|
| `DOMAIN` | `20-198-5-17.sslip.io` (your address) |
| `PUBLIC_URL` | `https://20-198-5-17.sslip.io` |
| `WEB_PORT` | `127.0.0.1:8088`, so the website is only reachable through HTTPS |
| `DB_PASSWORD`, `S3_SECRET_KEY`, `SUPER_ADMIN_PASSWORD` | New strong passwords |
| `JWT_SECRET` | A new 64-byte key (see the command below) |
| `WALLET_ENCRYPTION_KEY` | A new 32-byte key (see the command below). Never change it later. |
| `FRAUD_API_KEY` | Any long random text |
| `BLOCKCHAIN_ENABLED`, `BLOCKCHAIN_RPC_URL`, `BLOCKCHAIN_ADMIN_PRIVATE_KEY` | Your Sepolia values, to issue real certificates |

To make a key on the server, use this (64 for `JWT_SECRET`, 32 for `WALLET_ENCRYPTION_KEY`):

```
openssl rand -base64 64 | tr -d '\n'; echo
```

Save with `Ctrl+O`, `Enter`, then `Ctrl+X`. Keep the file private:

```
chmod 600 .env
```

**One wallet, one server.** The platform admin wallet keeps track of its own transactions. Don't run CredChain
with blockchain switched on, using the same admin key, on your laptop and on the server at the same moment.

## 6. Start it

```
docker compose --profile app --profile cloud up -d --build
```

The first build takes 5 to 15 minutes. Then check:

```
docker compose --profile app --profile cloud ps
```

Expected: seven containers. `credchain-backend` and `credchain-web` say `healthy`, and `credchain-proxy` is `Up`.

Open **https://20-198-5-17.sslip.io** (your address). The lock icon shows a valid certificate. Log in with
`SUPER_ADMIN_EMAIL` and `SUPER_ADMIN_PASSWORD`.

If the page does not load, read Caddy's log:

```
docker logs credchain-proxy --tail 50
```

"no such host" or a timeout from Let's Encrypt means `DOMAIN` is wrong or port 80 or 443 is still closed.

## Day-to-day

| Task | Command (in `credchain/infra`) |
|------|--------------------------------|
| See the backend's log | `docker logs credchain-backend --tail 100` |
| Update to the latest `main` | `git pull` then `docker compose --profile app --profile cloud up -d --build` |
| Stop everything | `docker compose --profile app --profile cloud down` (data is kept) |
| Back up the database | `docker exec credchain-db pg_dump -U credchain credchain > backup-$(date +%F).sql` |
| Read the test emails | On your laptop: `ssh -i .\your-key.pem -L 8025:127.0.0.1:8025 azureuser@20.198.5.17`, then open http://localhost:8025 |

**Emails.** Mailpit catches every email, so nothing reaches real inboxes. That is right for a demo. To send real
emails, put an SMTP service's values (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`,
`MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true`) in `.env`, then run the update command above.

**Costs.** A server bills while it runs, even when nobody uses CredChain. With student credit, stop (deallocate)
the virtual machine in the portal after the demo; the data stays on its disk.

## What is protected online

- HTTPS everywhere. Plain `http://` gets a redirect, and browsers are told to use HTTPS for a year (HSTS).
- Only the HTTPS proxy is reachable. The website, backend, database, file storage, AI service and Mailpit
  cannot be reached from the internet.
- Caddy passes on the visitor's real address and ignores any `X-Forwarded-For` a visitor sends, so the
  limits on logins and checks work per visitor. See `SECURITY.md` for the rest.
