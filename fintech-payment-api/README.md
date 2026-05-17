# Fintech Payment API

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)

A production-grade, high-concurrency payment engine built with Spring Boot. This API handles secure peer-to-peer transfers, multi-currency wallets, and detailed transaction auditing, with a heavy focus on financial integrity and system resilience under load.

---

## ⚡ Core Technical Strengths

This project isn't just about moving numbers between rows; it’s designed to handle the "hard parts" of fintech engineering:

*   **Pessimistic Concurrency Control**: Uses `PESSIMISTIC_WRITE` locks to prevent race conditions during balance updates.
*   **Deadlock Prevention**: Implements **Lock Ordering** by sorting Wallet UUIDs alphabetically before acquisition, ensuring the system never hangs during circular transfers.
*   **Idempotent Transfers**: Every transaction requires a `requestId`. The system detects duplicate requests to prevent double-spending on client retries.
*   **Rate Limiting & Security**: Protects sensitive endpoints (Auth, Wallets) using `Bucket4j` and Caffeine to throttle high-frequency traffic.
*   **Audit Trail**: Maintains an immutable ledger of all DEBIT and CREDIT operations with linked transaction references.

## 🏗 System Architecture
The application follows a clean N-tier architecture. For a deep dive into the engineering rationale and how the system scales, see:
👉 **[Architecture Documentation](docs/architecture.md)**

---

## 🚀 Getting Started

### 1. Prerequisites
- **JDK 17** or higher
- **Docker & Docker Compose** (for PostgreSQL)
- **Maven 3.8+**

### 2. Environment Setup
Create a `.env` file in the root directory (or update the provided template):

```env
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
POSTGRES_DB=fintechdb
DATABASE_URL=jdbc:postgresql://localhost:5432/fintechdb
JWT_SECRET=your_base64_secure_secret
JWT_EXPIRATION_MS=86400000
```

### 3. Spin up the Database
```bash
docker-compose up -d
```

### 4. Run the Application
```bash
mvn spring-boot:run
```
The API will be available at `http://localhost:8080`. You can explore the Swagger documentation at `http://localhost:8080/swagger-ui.html`.

## 📂 Documentation & API Flow
- [Architecture & Design Rationale](docs/architecture.md)
- [Database Schema](docs/database-schema.md)
- [API Usage Guide](docs/api-flow.md)
