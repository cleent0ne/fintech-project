# API Flow Documentation

## 1. Authentication & Authorization
The API uses **JWT (JSON Web Tokens)** for stateless authentication.

### Registration Flow
1. **Client** POSTs `RegisterRequest` to `/auth/register`.
2. **GlobalExceptionHandler** intercepts any validation errors (e.g., weak password, malformed email).
3. **AuthService** hashes the password using `BCrypt`.
4. **WalletService** creates default wallets (KES and USD) for the new user.
5. **Database** saves the User and Wallets in a single transaction.

### Login Flow
1. **Client** POSTs `LoginRequest` to `/auth/login`.
2. **AuthService** verifies credentials.
3. **JwtUtil** generates a token containing a `jti` (unique ID) and `sub` (email).

---

## 2. Wallet Operations

### Deposit Flow
1. **JwtAuthFilter** validates the Bearer token.
2. **WalletRateLimitFilter** checks if the user has exceeded their deposit limit.
3. **WalletService** increments the balance and creates a `CREDIT` transaction record.

### Transfer Flow (Critical Path)
1. **Validation**: Checks if sender has enough funds and receiver exists.
2. **Pessimistic Locking**: Acquires a `SELECT FOR UPDATE` lock on both wallets.
3. **Deadlock Prevention**: Always locks wallets in alphabetical order of their UUIDs.
4. **Atomic Update**: Subtracts from sender, adds to receiver, and creates linked `DEBIT`/`CREDIT` transaction records.
5. **Rollback**: Any failure (e.g., DB disconnect) triggers a full rollback of all wallet changes.
