# Build from the dashboard folder. NEXT_PUBLIC_* values are baked in at build time, so pass them as build args:
#   docker build --build-arg NEXT_PUBLIC_TELCO_API=http://localhost:8000 .     (the WebSocket address is derived from it)
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG NEXT_PUBLIC_TELCO_API=http://localhost:8000
ARG NEXT_PUBLIC_TELCO_WS=
# Where the dashboard's own server passes /radar/* on to (the main page's real numbers; next.config.mjs).
ARG RADAR_UPSTREAM=http://radar:8001
ENV NEXT_PUBLIC_TELCO_API=$NEXT_PUBLIC_TELCO_API NEXT_PUBLIC_TELCO_WS=$NEXT_PUBLIC_TELCO_WS RADAR_UPSTREAM=$RADAR_UPSTREAM
RUN npm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production HOSTNAME=0.0.0.0
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
CMD ["node", "server.js"]
