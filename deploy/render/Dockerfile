# Linda all-in-one, for Render's FREE plan (docs/DEPLOY.md, "Free on Render").
# One box runs everything the VPS runs as separate containers: the gateway (radar), the mock telco, the mock fraud
# team, the dashboard and Caddy in front. Free Render services cannot reach each other privately, so they live
# together here and talk over 127.0.0.1; only Caddy is reachable from outside, exactly as on the VPS.
# Build context: the repository root.

# 1. The dashboard, built once. "/telco" = the same server the page came from (src/lib/config.ts).
FROM node:22-slim AS dashboard
WORKDIR /app
COPY dashboard/package.json dashboard/package-lock.json ./
RUN npm ci
COPY dashboard/ .
ENV NEXT_PUBLIC_TELCO_API=/telco
RUN npm run build

# 2. Caddy's single program (the same version as the VPS).
FROM caddy:2.8-alpine AS caddy

# 3. The box: Python for the three services, plus Node and Caddy copied in.
FROM python:3.11-slim
WORKDIR /app
COPY --from=dashboard /usr/local/bin/node /usr/local/bin/node
COPY --from=caddy /usr/bin/caddy /usr/local/bin/caddy

# The three services pin the same versions, so one environment serves all of them.
COPY backend/requirements.txt /tmp/req-backend.txt
COPY simulator/requirements.txt /tmp/req-simulator.txt
COPY fraudintel/requirements.txt /tmp/req-fraudintel.txt
RUN pip install --no-cache-dir -r /tmp/req-backend.txt -r /tmp/req-simulator.txt -r /tmp/req-fraudintel.txt

# The same folder layout as the repository, so every service finds its files where it expects them.
COPY shared ./shared
COPY backend/src ./backend/src
COPY simulator/src ./simulator/src
COPY simulator/models ./simulator/models
COPY fraudintel/src ./fraudintel/src
COPY fraudintel/data ./fraudintel/data
COPY --from=dashboard /app/.next/standalone ./dashboard
COPY --from=dashboard /app/.next/static ./dashboard/.next/static
COPY deploy/Caddyfile ./deploy/Caddyfile
COPY deploy/render/start.sh ./deploy/render/start.sh

ENV PYTHONUNBUFFERED=1 NODE_ENV=production
CMD ["bash", "/app/deploy/render/start.sh"]
