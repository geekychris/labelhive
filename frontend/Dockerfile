# Stage 1: Vite production build.
FROM node:23-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Stage 2: nginx serving the built bundle + proxying /api to the backend service.
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
# Vite already copied public/* into dist; the chmod is a safety net for source files
# arriving with restrictive perms that nginx (non-root) couldn't otherwise read.
RUN chmod -R a+rX /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
