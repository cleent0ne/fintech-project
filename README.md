# Fintech Payment API

This project is a simulated payment platform (think M-Pesa or PayPal) built using Java and Spring Boot.

## 🚀 Key Features

*   **Multi-Currency Wallets**: Users get KES and USD wallets automatically upon registration.
*   **Secure P2P Transfers**: Move money between users safely with pessimistic locking to prevent double-spending and deadlocks.
*   **Robust Security**:
    *   JWT-based authentication with a transient blacklist for immediate logout.
    *   IP-based and per-user rate limiting to protect against brute-force attacks.
    *   Input validation and protection against common injection attacks.
*   **Audit Logs**: Every movement of money is recorded in a transaction history for transparency and auditing.
*   **Developer Friendly**: Full Swagger/OpenAPI documentation is included so you can explore the API easily.

## 🛠 Tech Stack

*   **Framework**: Spring Boot 3
*   **Database**: PostgreSQL (for production-style persistence)
*   **Caching**: Caffeine (for fast rate-limiting and blacklist checks)
*   **Documentation**: SpringDoc OpenAPI / Swagger UI
*   **Concurrency**: Pessimistic Locking & Idempotency Keys

## 📖 Getting Started

1. **Clone the repo**.
2. **Configure your database** in `application.yml` (or use the default settings).
3. **Run the app**: Navigate to the `fintech-payment-api` folder and run `mvn spring-boot:run`.
4. **Explore the API**: Head over to `http://localhost:8080/swagger-ui.html` to see the interactive documentation.
