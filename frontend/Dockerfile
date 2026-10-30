# CredChain website: built with Vite, served by nginx, which also passes /api/ to the backend
#   docker build -t credchain-web frontend
# docker compose --profile app (infra/docker-compose.yml) builds and starts it with the rest of the app.

FROM node:22-alpine AS build
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Empty = call the API on the same address as the website (nginx forwards /api/ to the backend)
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM nginx:1.28-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
