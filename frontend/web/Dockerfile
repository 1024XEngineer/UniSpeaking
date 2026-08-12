FROM node:22-alpine AS build

WORKDIR /app

ARG VITE_BACKEND_URL=/backend
ARG VITE_FEEDBACK_URL=
ARG VITE_ALIYUN_CAPTCHA_SCENE_ID=
ARG VITE_ALIYUN_CAPTCHA_PREFIX=
ARG VITE_ALIYUN_CAPTCHA_REGION=cn
ARG NPM_REGISTRY=https://registry.npmmirror.com
ENV VITE_BACKEND_URL=${VITE_BACKEND_URL}
ENV VITE_FEEDBACK_URL=${VITE_FEEDBACK_URL}
ENV VITE_ALIYUN_CAPTCHA_SCENE_ID=${VITE_ALIYUN_CAPTCHA_SCENE_ID}
ENV VITE_ALIYUN_CAPTCHA_PREFIX=${VITE_ALIYUN_CAPTCHA_PREFIX}
ENV VITE_ALIYUN_CAPTCHA_REGION=${VITE_ALIYUN_CAPTCHA_REGION}

COPY package.json package-lock.json ./
RUN npm ci \
    --registry=${NPM_REGISTRY} \
    --fetch-retries=5 \
    --fetch-retry-mintimeout=10000 \
    --fetch-retry-maxtimeout=120000

COPY . .
RUN npm run check:routes \
    && npm run check:realtime-events \
    && npm run build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
