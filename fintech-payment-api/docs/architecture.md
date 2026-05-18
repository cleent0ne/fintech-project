# Engineering Rationale & Architecture

This document outlines the design decisions behind the Fintech Payment API. My goal was to build a system that prioritizes transactional integrity and security while remaining easy to scale.

## The Stack
I chose an N-tier architecture using Spring Boot and PostgreSQL. It’s a standard, reliable pattern that separates the API logic from the heavy lifting in the service layer. Authentication is stateless, handled via JWTs, which keeps the system fast and ready for horizontal scaling.

## Handling Concurrency (The Hard Part)
When you're dealing with money, you can't rely on simple database updates. I spent a lot of time on the `WalletService.transfer` logic to handle three specific risks:

1. **Race Conditions**: I used `PESSIMISTIC_WRITE` locks on the wallet rows. This ensures that only one transaction can touch a specific wallet balance at a time.
2. **Deadlocks**: To prevent the classic "circular wait" problem (where two users transfer to each other at the same time and freeze the system), I implemented Lock Ordering. By sorting the Wallet UUIDs and always locking them in a fixed alphabetical order, I ensured that threads never fight for the same resources in a way that causes a hang.
3. **Double-Spending**: I added an idempotency check using a `requestId`. If a client's internet cuts out and they retry a request, the system detects the duplicate and returns the original success message instead of deducting money a second time.

## Security & Token Management
Authentication is handled by Spring Security, but I added a few custom layers:

- **Token Revocation**: Since JWTs are stateless, they can't be "invalidated" easily. I built a `TokenBlacklistService` that stores revoked tokens in a `ConcurrentHashMap` until they naturally expire. For this project, an in-memory map is perfect, but I've designed it so that swapping it out for Redis in a multi-node environment would be straightforward.
- **Rate Limiting**: I used `Bucket4j` and Caffeine to throttle traffic to sensitive endpoints like login and transfers. This protects against brute-force attempts and basic DDoS load.

## Performance Optimizations

To ensure high read/write throughput and reduce latency under heavy concurrent request volume, we implemented two key performance optimizations in the persistence layer:

1. **N+1 Query Elimination**: In looking up a user's wallet balances, the default JPA lazy loading fired multiple database queries to fetch the associated `User` profile. We eliminated this N+1 query issue by introducing an `@EntityGraph(attributePaths = {"user"})` configuration on the repository lookup, forcing Hibernate to eager-fetch the user details inside a single joined SQL statement.
2. **Database Schema Indexing**:
   - Added a composite B-tree index `idx_tx_wallet_created` on `(wallet_id, created_at DESC)` in `transactions` to optimize sorted, paginated transaction history queries.
   - Added `idx_tx_reference` on `(reference)` to ensure fast transactional reference lookup and idempotency validation.
   - Added `idx_wallet_user_currency` on `(user_id, currency)` in `wallets` to boost concurrent read and write operations.


## Where This Goes Next
While this version is stable and highly performant on a single node, there are clear paths for growth:
- Moving the blacklist and rate-limiting buckets to Redis to support a distributed cluster.
- Introducing a "System Integrity" check that reconciles total wallet balances against the transaction ledger to ensure 100% money conservation.
- Transitioning the audit trail to an event-sourced model for even deeper financial logging.

