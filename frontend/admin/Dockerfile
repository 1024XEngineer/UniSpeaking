FROM node:22-alpine AS build

WORKDIR /app

ARG VITE_BACKEND_URL=/backend
ARG NPM_REGISTRY=https://registry.npmjs.org
ENV VITE_BACKEND_URL=${VITE_BACKEND_URL}

COPY package.json package-lock.json ./
RUN npm ci --registry=${NPM_REGISTRY} \
    --fetch-retries=5 \
    --fetch-retry-mintimeout=10000 \
    --fetch-retry-maxtimeout=120000

COPY . .
RUN npm run lint \
    && VITE_BACKEND_URL= npm run test \
    && npm run build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
