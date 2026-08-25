ARG DOCKER_IMAGE_REGISTRY=docker.m.daocloud.io

FROM ${DOCKER_IMAGE_REGISTRY}/library/node:22-alpine AS build

WORKDIR /app

ARG VITE_BACKEND_URL=/backend
ARG VITE_GRAFANA_URL=http://218.11.5.202:3001
ARG NPM_REGISTRY=https://registry.npmmirror.com
ENV VITE_BACKEND_URL=${VITE_BACKEND_URL}
ENV VITE_GRAFANA_URL=${VITE_GRAFANA_URL}

COPY package.json package-lock.json ./
RUN npm ci --registry=${NPM_REGISTRY} \
    --fetch-retries=5 \
    --fetch-retry-mintimeout=10000 \
    --fetch-retry-maxtimeout=120000

COPY . .
RUN npm run lint \
    && VITE_BACKEND_URL= npm run test \
    && npm run build

FROM ${DOCKER_IMAGE_REGISTRY}/library/nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
