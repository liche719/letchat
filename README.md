# LetChat

LetChat is a full-stack chat application with a Spring Boot backend and a Vue 3 desktop/web frontend. It supports account login, contacts, group creation and member management, WebSocket messaging, paginated history, and authenticated media downloads.

For resume and interview preparation, see [Letchat 简历证据链与面试讲法](docs/resume-project-evidence.md). It summarizes the project background, backend responsibilities, technical tradeoffs, code evidence, runtime validation, and interview-ready STAR answers.

## Project Structure

```text
letchat/
+-- letchat_java/                 # Spring Boot backend
+-- letchat_desktop/
|   +-- letchat-vue3/             # Vue 3 + Vite frontend
|   +-- API_MAPPING.md            # API notes
|   +-- api-schema.json           # API schema export
+-- files/                        # Local runtime uploads, ignored by git
+-- README.md
```

## Tech Stack

- Backend: Java 21, Spring Boot 3.3.5, MyBatis, MySQL, Redis, RabbitMQ, Netty WebSocket, MinIO
- Frontend: Vue 3, TypeScript, Vite, Element Plus, Pinia, Vue Router, Axios
- Build tools: Maven, npm

## Configuration

Sensitive local configuration is intentionally not committed.

- Backend example config: `letchat_java/src/main/resources/application.example.yaml`
- Backend local config: copy the example to `letchat_java/src/main/resources/application.yaml` and set your MySQL, Redis, RabbitMQ, admin email, and file storage values.
- Frontend example env: `letchat_desktop/letchat-vue3/.env.example`
- Frontend local env: copy it to `letchat_desktop/letchat-vue3/.env` if you need local overrides.

The default development ports are:

- Backend HTTP API: `http://localhost:7070/api`
- Backend WebSocket: `ws://localhost:7071/ws`
- Frontend dev server: `http://localhost:5173`

## Backend Setup

Requirements:

- JDK 21 recommended and verified
- Maven
- MySQL
- Redis
- RabbitMQ

Run the backend:

```bash
cd letchat_java
mvn spring-boot:run
```

Build a jar:

```bash
cd letchat_java
mvn clean package
```

## Frontend Setup

Requirements:

- Node.js 20.19+ or 22.12+
- npm

Run the frontend:

```bash
cd letchat_desktop/letchat-vue3
npm install
npm run dev
```

Build the frontend:

```bash
cd letchat_desktop/letchat-vue3
npm run build
```

## Reproducible Verification

The repository includes an isolated Docker test environment and repeatable runtime checks:

```powershell
docker compose -p letchat -f docker-compose-test.yml up -d
.\scripts\prepare_test_data.ps1
.\scripts\runtime_smoke_test.ps1
.\scripts\concurrency_smoke_test.ps1
.\scripts\group_lifecycle_smoke_test.ps1
```

The runtime smoke test covers authentication, registration and login, two WebSocket clients, real-time private messaging, RabbitMQ persistence, history loading, and queue drain verification. The group-lifecycle smoke test creates a disposable group, adds and removes a member, verifies group-message persistence and access revocation, then dissolves the group. The backend unit tests also cover group-owner authorization, member removal, owner-leave protection, and avatar fallback handling.

For the chat-history query benchmark, a fixed dataset of 20,000 messages in one private session was tested with 16 concurrent workers and 5,000 formal requests per run. Across three local runs, the median reached 190.57 QPS with 157.29 ms p95 latency and 15,000 successful formal requests. The complete environment, raw results, calculation, and reproduction commands are recorded in [Chat history performance evidence](docs/chat-history-performance.md).

## Notes

- `node_modules/`, Java `target/`, local `.env` files, backend `application.yaml`, and runtime upload files are ignored by git.
- The frontend Vite proxy forwards `/api` to `http://localhost:7070` and `/socket.io` to `http://localhost:7071`.
- If you change backend ports, update the frontend environment or Vite proxy settings accordingly.
