# CodeTest – Product & Discount Services

This project consists of two Kotlin-based microservices:

- **Product Service** – Manages product catalog and product data.
- **Discount Service** – Manages discounts that can be applied to products.

Both services are written in Kotlin with Ktor, built with Gradle, and designed to run together using Docker Compose.

---

## 🚀 Running the Services

### Prerequisites
- Docker & Docker Compose (Docker Desktop on macOS/Windows)
- Java 21 (for local builds without Docker)

### Build & Run with Docker

From the project root:

```bash
docker compose up --build
```

- Product Service: http://localhost:8080  
- Discount Service: http://localhost:8081  

### Example Requests

```bash
# Health checks
curl -H "Authorization: Bearer secret-dev-token-please-change" http://localhost:8080/health
curl -H "Authorization: Bearer secret-dev-token-please-change" http://localhost:8081/health

# Get products
curl -H "Authorization: Bearer secret-dev-token-please-change" http://localhost:8080/products

# Apply discount
curl -X POST -H "Authorization: Bearer secret-dev-token-please-change"   -H "Content-Type: application/json"   -d '{"productId":"123", "discountId":"DISC10", "percent":10}'   http://localhost:8081/discounts/apply
```

---

## 📥 Ingestion Controls

The project supports **data ingestion workflows** for product and discount data:

- **Start ingestion** → Triggered by API call (e.g., `/admin/ingest/{id}/start`)
- **Parse** → Raw data is parsed into structured objects.
- **Validate** → Business rules and schema validation applied.
- **Write** → Data stored in memory or persistent storage.
- **Status** → Ingestion status retrievable via `/admin/ingest/{id}/status`.

This design allows monitoring ingestion pipelines while keeping services decoupled.
