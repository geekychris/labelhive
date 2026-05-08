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
COPY --from=builder /app/public/label_hive.png /usr/share/nginx/html/label_hive.png
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
