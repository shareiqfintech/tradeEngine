# syntax=docker/dockerfile:1

# ---------- Stage 1: build the static React app ----------
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies first so this layer is cached across code-only changes.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# VITE_API_BASE_URL is a BUILD-time arg because Vite inlines import.meta.env
# values into the static bundle. Leave it unset (default) so the frontend
# calls relative "/api" paths, which Nginx (below) proxies to the backend -
# no CORS setup required, and no backend URL baked into the JS bundle.
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ---------- Stage 2: serve the static build with Nginx ----------
FROM nginx:1.27-alpine AS runtime

# Default upstream assumes a docker-compose service named "backend" on 8080
# (see the project's docker-compose.yml) - override at `docker run`/compose
# time with `-e BACKEND_UPSTREAM=host:port`, no rebuild needed. Templated
# into /etc/nginx/conf.d/default.conf by the base image's own entrypoint.
ENV BACKEND_UPSTREAM=backend:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s CMD wget -qO- http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
