# Architecture Overview

This project implements a **two-service microservice architecture**:

- **Product Service**
  - Stores and serves product data
  - Provides endpoints for querying products
  - Exposes ingestion endpoints for product data

- **Discount Service**
  - Manages discount rules
  - Provides endpoints for applying discounts
  - Integrates with Product Service for validating product references

---

## ⚖️ Design & Trade-offs

- **Kotlin + Ktor**: Chosen for lightweight, coroutine-based async server design.
- **Two services**: Clear separation of concerns (products vs. discounts).  
  Trade-off: additional overhead in inter-service communication.
- **In-memory storage**: Simplifies implementation for this test.  
  Trade-off: not persistent, but enough to demonstrate architecture.
- **Docker Compose**: Simplifies running both services locally.  
  Trade-off: limited scalability vs. Kubernetes in production.

---

## 🔄 Sequence Diagrams

### GET /products

```mermaid
sequenceDiagram
    participant Client
    participant ProductService

    Client->>ProductService: GET /products
    ProductService-->>Client: 200 OK + List of Products
```

---

### POST /products/{id}/discount

```mermaid
sequenceDiagram
    participant Client
    participant DiscountService
    participant ProductService

    Client->>DiscountService: POST /products/{id}/discount
    DiscountService->>ProductService: Validate Product Exists
    ProductService-->>DiscountService: Product Found/Not Found
    DiscountService-->>Client: 200 OK (applied) or 400 Bad Request
```

---

### Ingestion Workflow

```mermaid
sequenceDiagram
    participant Client
    participant ProductService
    participant IngestManager

    Client->>ProductService: POST /admin/ingest/{id}/start
    ProductService->>IngestManager: Start ingestion
    IngestManager->>IngestManager: Parse data
    IngestManager->>IngestManager: Validate records
    IngestManager->>IngestManager: Write data
    ProductService-->>Client: 202 Accepted (ingestion started)
    Client->>ProductService: GET /admin/ingest/{id}/status
    ProductService-->>Client: Ingestion status
```

---

## 🧩 Component Diagram

```mermaid
flowchart LR
    subgraph Product Service
        PAPI[API Layer]
        PIngest[Ingestion Manager]
        PStore[(Product Store)]
    end

    subgraph Discount Service
        DAPI[API Layer]
        DStore[(Discount Store)]
    end

    Client[Client/Browser]

    Client --> PAPI
    Client --> DAPI

    DAPI --> PAPI
    PAPI --> PStore
    PIngest --> PStore
```
