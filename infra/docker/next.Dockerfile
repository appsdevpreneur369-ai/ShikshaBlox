# syntax=docker/dockerfile:1.7
# Builds one Next.js app from the pnpm workspace. Usage: --build-arg APP=site|admin|www
ARG NODE_VERSION=22-alpine

FROM node:${NODE_VERSION} AS deps
RUN npm i -g pnpm@10.34.9
WORKDIR /repo
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json .npmrc ./
COPY packages/config/package.json packages/config/
COPY packages/ui/package.json packages/ui/
COPY packages/blocks/package.json packages/blocks/
COPY packages/api-client/package.json packages/api-client/
COPY apps/site/package.json apps/site/
COPY apps/admin/package.json apps/admin/
COPY apps/www/package.json apps/www/
COPY e2e/package.json e2e/
RUN --mount=type=cache,id=pnpm,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter "./apps/*..." --filter "./packages/*"

FROM deps AS build
ARG APP
# apps/www pages are statically generated, so these are baked in at build time (ignored by other apps).
ARG SB_WWW_SITE_URL=""
ARG SB_WWW_NOINDEX=""
ARG SB_WWW_MORIAH_URL=""
ARG SB_WWW_STUDIO_URL=""
ARG SB_WWW_WHATSAPP=""
ARG SB_WWW_PHONE=""
ARG SB_WWW_EMAIL=""
COPY packages/ packages/
COPY apps/${APP}/ apps/${APP}/
ENV NEXT_TELEMETRY_DISABLED=1 SB_WWW_SITE_URL=${SB_WWW_SITE_URL} SB_WWW_NOINDEX=${SB_WWW_NOINDEX}     SB_WWW_MORIAH_URL=${SB_WWW_MORIAH_URL} SB_WWW_STUDIO_URL=${SB_WWW_STUDIO_URL} SB_WWW_WHATSAPP=${SB_WWW_WHATSAPP}     SB_WWW_PHONE=${SB_WWW_PHONE} SB_WWW_EMAIL=${SB_WWW_EMAIL}
RUN pnpm --filter "@shikshablox/${APP}" build

FROM node:${NODE_VERSION} AS runner
ARG APP
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /repo/apps/${APP}/.next/standalone ./
COPY --from=build --chown=app:app /repo/apps/${APP}/.next/static ./apps/${APP}/.next/static
COPY --from=build --chown=app:app /repo/apps/${APP}/public ./apps/${APP}/public
USER app
ENV APP_DIR=apps/${APP}
CMD ["sh", "-c", "node $APP_DIR/server.js"]
