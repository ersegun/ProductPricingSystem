
# Product Pricing System

## Overview
A simple product pricing system with two microservices built with Kotlin + Ktor for managing product prices and discounts.

**Services**
1. **Product Service** (port 8080) – applies VAT + discounts, handles ingestion.  
2. **Discount Service** (port 8081) – stores applied discounts, enforces idempotency.

Run locally via Gradle or Docker Compose.

---

## Quick Start

### Prerequisites
Java 21, Gradle 8, Docker.

### Gradle
```bash
./gradlew discount-service:run
./gradlew product-service:run
```

### Docker Compose
Services can be run with docker compose, and make sure that Docker Desktop is up and running locally
```bash
docker compose up ‑‑build
```

Services:  
• Product → http://localhost:8080  
• Discount → http://localhost:8081

Auth header required:  
`Authorization: Bearer secret‑dev‑token‑please‑change`

---

## Tests
```bash
./gradlew test
```

Reports: `build/reports/tests/test/index.html`

---

## Design Highlights
- **Separation of concerns:** Product and Discount logic are placed as microservices.
- **In‑memory stores** via `ConcurrentHashMap`.  (I did not implement a database connection for this task.)
- **Kotlin coroutines** for parallel ingest.
- **Docker Compose** for simple local orchestration.  


