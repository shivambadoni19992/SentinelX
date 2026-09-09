FROM node:18-alpine

WORKDIR /app

COPY ../package.json ./
RUN npm ci --only=production

COPY ../service-template.js ./server.js
COPY . .

ENV SERVICE_NAME=risk
ENV PORT=8080

EXPOSE $PORT

HEALTHCHECK --interval=10s --timeout=5s --retries=5   CMD wget -q --spider http://localhost:$PORT/health || exit 1

CMD ["node", "server.js"]
