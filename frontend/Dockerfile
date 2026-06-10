# ─────────────────────────────────────────────
# Stage 1: Build the React app with Vite
# ─────────────────────────────────────────────
FROM node:20-alpine AS build

WORKDIR /app

# Copy package files first — Docker caches this layer.
# If package.json hasn't changed, npm install is skipped on rebuilds.
# This is the same dependency-caching trick we used in the Spring Boot Dockerfile.
COPY package.json package-lock.json* ./

RUN npm ci --silent

# Copy source files and build
# VITE_API_URL tells the React app where to send API requests.
# In Docker Compose, Nginx proxies /api to the Spring Boot container.
COPY . .

ARG VITE_API_URL=/api
ENV VITE_API_URL=$VITE_API_URL

RUN npm run build

# ─────────────────────────────────────────────
# Stage 2: Serve the built files with Nginx
# ─────────────────────────────────────────────
# We don't need Node.js at runtime — just a web server to serve static files.
# Nginx is perfect: small, fast, handles client-side routing correctly.
FROM nginx:alpine AS runtime

# Copy the Vite build output into Nginx's web root
COPY --from=build /app/dist /usr/share/nginx/html

# Copy a custom Nginx config for the frontend.
# The critical part: all routes return index.html so React Router works.
# Without this, refreshing on /flags would give a 404.
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# Nginx runs in the foreground so Docker can manage the process
CMD ["nginx", "-g", "daemon off;"]