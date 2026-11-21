# Admin web (docs/PHASE20.md section 5): build the static export, then serve it with Caddy, which
# sets the security headers. Build args are public values baked into the bundle.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG NEXT_PUBLIC_API_BASE
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID
ARG NEXT_PUBLIC_ENV_LABEL
ENV NEXT_PUBLIC_API_BASE=$NEXT_PUBLIC_API_BASE NEXT_PUBLIC_GOOGLE_CLIENT_ID=$NEXT_PUBLIC_GOOGLE_CLIENT_ID NEXT_PUBLIC_ENV_LABEL=$NEXT_PUBLIC_ENV_LABEL
RUN npm run build:host

FROM caddy:2-alpine
COPY --from=build /app/out /srv
COPY --from=build /app/deploy/Caddyfile /etc/caddy/Caddyfile
