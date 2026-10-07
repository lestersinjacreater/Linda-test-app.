# Deploying Linda on a VPS

One small server runs everything: the radar, the mock telco, the dashboard and the database. A web server called
**Caddy** sits in front, gives you free HTTPS, and is the **only** thing the internet can reach.

```
 phones (Linda app) ──https://DOMAIN/radar/*──┐
 audience browser ────https://DOMAIN/ ────────┤
 presenter ───────────https://DOMAIN/telco/scenarios/* (password) ─┤
                                              ▼
                                   ┌──────── Caddy :80 / :443 ────────┐   private docker network
                                   │   /          → dashboard :3000    │
                                   │   /radar/*   → radar     :8001 ──────► postgres
                                   │   /telco/*   → telco     :8000 ◄──── radar calls /network/confirm
                                   └───────────────────────────────────┘
```

Only these simulator paths are public: `/telco/events` (the live feed), `/telco/metrics`, `/telco/population`, `/telco/health`.
Starting or resetting a demo needs the presenter password. Everything else (`/network/confirm`, `/deliveries`, `/history`)
cannot be reached from outside.

## 1. Get a server

| What | Recommendation |
|---|---|
| Size | 1 vCPU, 2 GB RAM, 20 GB disk (1 GB works but building the images is slow) |
| System | Ubuntu 24.04 LTS (22.04 also works) |
| Where | Any provider: DigitalOcean, Hetzner, AWS Lightsail, Google Cloud, Azure. Pick a region near Nairobi if you can |
| Cost | roughly 5 to 12 USD a month. Delete the server after the hackathon |
| Domain | **Strongly recommended**: a name like `linda.yourdomain.com` gives automatic HTTPS. Without one, use the IP address and plain http (fine for rehearsals, see "No domain" below) |

Ask the Safaricom interns' organisers first: they may provide hosting.

## 2. One-time server setup

Log in to the server (`ssh root@SERVER_IP`), then:

```bash
# a normal user instead of root
adduser linda && usermod -aG sudo linda
rsync --archive --chown=linda:linda ~/.ssh /home/linda        # copy your SSH key to the new user

# firewall: only SSH, http, https
ufw allow OpenSSH && ufw allow 80 && ufw allow 443/tcp && ufw allow 443/udp && ufw --force enable

# Docker (official installer) and git
curl -fsSL https://get.docker.com | sh
usermod -aG docker linda
apt-get install -y git make python3
```

Log out, log back in as `linda` (`ssh linda@SERVER_IP`), then get the code:

```bash
git clone https://github.com/lestersinjacreater/Linda-test-app..git linda    # use your real repo address
cd linda && git checkout claude/zealous-shannon-gz3o6g                       # or main, once merged
```

## 3. Point the domain at the server

At your domain provider, add an **A record**: name `linda` (or whatever you chose), value = the server's IP address.
Wait a few minutes, then check from your own computer: `ping linda.yourdomain.com` shows the server's IP.
Caddy cannot get a certificate until this works, and ports 80 and 443 must be open (step 2).

## 4. Fill in `.env.production`

```bash
cp .env.production.example .env.production
chmod 600 .env.production            # only you can read it
openssl rand -hex 24                 # a database password: paste it as POSTGRES_PASSWORD
docker run --rm caddy:2.8-alpine caddy hash-password --plaintext 'a-long-presenter-password'
                                     # paste the output (starts with $2a$) as PRESENTER_HASH, keeping the single quotes
nano .env.production                 # set SITE_ADDRESS, PUBLIC_URL, PRESENTER_USER, PRESENTER_HASH, POSTGRES_PASSWORD
```

With a domain: `SITE_ADDRESS=linda.yourdomain.com` and `PUBLIC_URL=https://linda.yourdomain.com`.
Write the presenter password somewhere the presenters can find it. **Never commit `.env.production`** (it is git-ignored).

## 5. Start it

```bash
make prod-up           # builds the images (5 to 10 minutes the first time) and starts everything
make prod-ps           # every service should say "healthy" after a minute
make prod-logs         # live logs; Ctrl+C to leave (the services keep running)
```

The first HTTPS certificate takes about half a minute after Caddy starts.

## 6. Check it works (do this before every rehearsal)

```bash
make prod-check BASE=https://linda.yourdomain.com PRESENTER_USER=presenter PRESENTER_PASS='a-long-presenter-password'
```

It looks at the server from outside, checks that the private paths are closed and the password is enforced, plays one scam
blast, and checks that the radar confirmed the number. You want `ALL CHECKS PASSED`. **It plays a blast, so do not run it
while presenting.** Then open `https://linda.yourdomain.com` in a browser: the badge should say **LIVE**.

## 7. Connect the phones

In the Linda app: **Settings, tap the version number 7 times**, then under Developer set the radar address to
`https://linda.yourdomain.com/radar` and tap **Save and sync now**. Also tap **Agree** on the consent card (or the switch in
Settings), otherwise the phone sends no reports. A phone only reports real SMS from real phone numbers, never demo-mode messages.

## Day to day

| Task | Command |
|---|---|
| See what is running | `make prod-ps` |
| Watch logs | `make prod-logs` |
| Restart one service | `docker compose -f docker-compose.prod.yml --env-file .env.production restart telco` |
| Update to new code | `git pull && make prod-up` |
| Stop everything | `make prod-down` (data is kept) |
| Wipe all radar data (fresh start) | `make prod-down && docker volume rm linda_pgdata` |
| Change `PUBLIC_URL` or the domain | edit `.env.production`, then `make prod-up` (the dashboard is rebuilt with the new address) |

Repeating the demo needs no wipe: each run uses fresh scammer numbers and fresh device ids, so earlier runs cannot change the result.

## No domain (IP address only)

Set `SITE_ADDRESS=:80` and `PUBLIC_URL=http://SERVER_IP`. You get plain http, so the presenter password is sent unencrypted:
use a throwaway password and only for rehearsals. The Android app allows http, so phones work. In the app, use `http://SERVER_IP/radar`.

## Backup plan: your laptop

Same files, same commands, on a laptop with Docker: `SITE_ADDRESS=:80`, `PUBLIC_URL=http://LAPTOP_IP` (the laptop's address on the
venue Wi-Fi), `make prod-up`. Phones must be on the same Wi-Fi and use `http://LAPTOP_IP/radar`. If even that fails, the dashboard
still shows its recorded replay and the Android app's demo mode needs no network at all.

## Security notes (read once)

- **Public by design:** the dashboard, the live feed, `/radar/*` (phones must reach it). The radar accepts only the fields in contract 5.1, rejects anything with message text, and rate-limits each device.
- **Protected:** starting or resetting demos (presenter password). The database and the radar's confirm callback are not reachable from outside.
- **Demo-only shortcut:** `RADAR_DEMO_TRUSTED_PREFIX=5111` makes devices whose id starts with `5111` start with high trust, which the simulated phones need to get a first confirmation. Real phones have random ids, so this almost never matches, but someone who knows the prefix could abuse it. Set it to empty after the hackathon, and tell judges plainly what it is.
- **No secrets in git.** `.env.production` stays on the server. Rotate the database and presenter passwords after the event, or delete the server.
- **No Daraja or Africa's Talking keys are configured.** Those features are not built yet; when they are, their sandbox keys go in `.env.production` only.
- The server holds sender phone numbers (fake placeholders in the simulation, real numbers if real phones report). Never message text. Delete the server and volumes when the event is over.

## When something is wrong

| Symptom | Likely cause and fix |
|---|---|
| Browser says certificate error or the site does not load | The A record does not point at the server yet, or ports 80/443 are closed. `make prod-logs`, look for "certificate" lines from caddy. |
| 502 Bad Gateway | A service is not healthy. `make prod-ps`, then `docker compose -f docker-compose.prod.yml --env-file .env.production logs radar`. |
| Dashboard badge says **REPLAY** | The browser cannot reach `/telco/events`. Most often `PUBLIC_URL` is wrong or was changed without rebuilding (`make prod-up`). |
| Start blast asks for a password and then does nothing | Wrong password, or the hash in `.env.production` lost its single quotes. |
| `prod-check` says the radar never confirmed | `RADAR_DEMO_TRUSTED_PREFIX` and `SIM_DEVICE_PREFIX` differ, or the prefix was emptied. They must match for the simulator's phones. |
| Server runs out of disk | `docker system prune -af` (removes unused images; keeps running services and volumes). |

## What was and was not tested

Tested here: the compose file resolves and refuses to start without secrets; Caddy accepts the Caddyfile; the whole stack
(radar on **real Postgres 16**, simulator, dashboard) behind the **real Caddy** passes `smoke_prod.py`, including closed private
paths, the presenter password, and a live dashboard through Caddy's WebSocket proxy. Testing on Postgres found and fixed a bug
that SQLite had hidden (see `docs/EXPLAINED.md`).
**Not tested (no Docker daemon in the development environment):** building the container images, and Let's Encrypt issuing a real
certificate. The CI job "deploy config" checks the compose file and Caddyfile on every change; the first `make prod-up` on a real server is the
real test of the images, so do it days before the event, not on the morning of it.
