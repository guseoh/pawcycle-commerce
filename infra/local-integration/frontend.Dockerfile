FROM node:24.18.0-alpine3.23 AS dependencies

WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

FROM node:24.18.0-alpine3.23 AS build

WORKDIR /app
COPY --from=dependencies /app/node_modules ./node_modules
COPY frontend ./
# Toss Test client key (public test_ck_ value) is inlined at build time only when
# compose.toss-test.yaml supplies it. Empty by default: the payment widget stays disabled.
ARG NEXT_PUBLIC_TOSS_TEST_CLIENT_KEY=
ENV NEXT_PUBLIC_TOSS_TEST_CLIENT_KEY=$NEXT_PUBLIC_TOSS_TEST_CLIENT_KEY
RUN npm run build

FROM node:24.18.0-alpine3.23

ENV NODE_ENV=production
WORKDIR /app
COPY --from=build --chown=node:node /app/package.json /app/package-lock.json /app/next.config.ts ./
COPY --from=build --chown=node:node /app/node_modules ./node_modules
COPY --from=build --chown=node:node /app/.next ./.next

USER node
EXPOSE 3000
CMD ["npm", "run", "start", "--", "-H", "0.0.0.0", "-p", "3000"]
