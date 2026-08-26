# SentinelX Frontend — Vite dev server (port 5173 inside the container).
# Live reload + built-in proxy to the API Gateway on the shared docker network.
FROM node:20-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install --no-audit --no-fund

COPY . .

ENV VITE_API_BASE=http://api-gateway:8080
EXPOSE 5173

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173", "--strictPort"]