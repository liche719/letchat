# LetChat

LetChat is a full-stack chat application with a Spring Boot backend and a Vue 3 desktop/web frontend. It supports account login, contacts, group chats, WebSocket messaging, and file-style message flows.

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

- Backend: Java 8, Spring Boot 2.6.1, MyBatis, MySQL, Redis, RabbitMQ, Netty WebSocket
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

- JDK 8
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

## Notes

- `node_modules/`, Java `target/`, local `.env` files, backend `application.yaml`, and runtime upload files are ignored by git.
- The frontend Vite proxy forwards `/api` to `http://localhost:7070` and `/socket.io` to `http://localhost:7071`.
- If you change backend ports, update the frontend environment or Vite proxy settings accordingly.
