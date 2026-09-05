# SmartCampus ERP - frontend production image (scope 71).
#
# Multi-stage: Node build -> static bundle served by nginx. No secret belongs in
# this image at all - the frontend never holds the AI key, JWT secret, SMTP
# password, DB password or Judge0 key (§25, §61); the only build-time value is
# VITE_API_BASE_URL, which is not a secret.
#
# VITE_API_BASE_URL defaults to "" (relative), which is the correct value when this
# image is run behind the bundled nginx.conf reverse proxy (see docker-compose.prod.yml)
# - the browser calls same-origin /api/... and nginx forwards it to the backend
# container, so the deployed frontend never needs to know the backend's real host.
# Override only if serving the frontend from somewhere that does NOT proxy /api
# itself (e.g. a separate CDN in front of a directly-reachable backend origin):
#   docker build --build-arg VITE_API_BASE_URL=https://api.example.com \
#     -t smartcampus-frontend -f frontend/Dockerfile frontend
#
# Build (default, same-origin proxy setup):
#   docker build -t smartcampus-frontend -f frontend/Dockerfile frontend
# Run standalone (normally run via docker-compose.prod.yml instead, so /api resolves):
#   docker run -p 8080:80 smartcampus-frontend

# ---------------------------------------------------------------------------
# Stage 1: build the static bundle with Vite
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /build

# package*.json first so `npm ci` is cached in its own layer.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---------------------------------------------------------------------------
# Stage 2: serve dist/ with nginx
# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:80/ >/dev/null || exit 1
