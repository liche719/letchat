# LetChat server deployment

This deployment exposes only Nginx on port `80`. The API, Netty WebSocket server,
MySQL, Redis, RabbitMQ, and MinIO remain on the internal Docker network.

## Build the release bundle

```powershell
cd letchat_java
mvn clean package
cd ..\letchat_desktop\letchat-vue3
npm ci
npm run build
```

Copy the repository files, the generated backend JAR, and the generated frontend
`dist` directory to the server. Create `deploy/.env` from `.env.example` and use
unique passwords for every required variable.

## Start and check

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml up -d --build
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml ps
curl -fsS http://127.0.0.1/api/actuator/health
```

## TLS

An IP address cannot receive a normal public CA certificate. Keep this as HTTP only
for temporary verification. Before public use, bind a domain name and place a TLS
certificate in front of Nginx; then the frontend WebSocket must use `wss`.
