# E-Commerce Backend

A production-style e-commerce backend built with Spring Boot 3 and Java 21. It covers the full
shopping journey: catalogue browsing and search, signup/login with session tokens, a per-user cart,
order placement with stock reservation, and hosted-checkout payments through Razorpay or Stripe.
A Redis read-through cache serves the high-demand ("popular") products.

Built by **Shivendra Pratap Singh** as the capstone project for the Master of Science in Computer Science,
Scaler Neovarsity - Woolf.

---

## Table of contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Tech stack](#tech-stack)
4. [Getting started](#getting-started)
5. [Configuration](#configuration)
6. [API reference](#api-reference)
7. [Walkthrough with curl](#walkthrough-with-curl)
8. [Testing](#testing)
9. [Benchmark: Redis popular-products cache](#benchmark-redis-popular-products-cache)
10. [Project structure](#project-structure)
11. [Deployment on AWS](#deployment-on-aws)
12. [Design decisions](#design-decisions)

---

## Features

| Area | What it does |
|---|---|
| Catalogue | CRUD on products and categories, paged and sorted listing, soft delete |
| Pluggable product source | `product.source=self` (own DB, default) or `fakestore` (proxy to the public FakeStore API) |
| Search | Case-insensitive keyword search over title and description, with allow-listed sort fields and a capped page size |
| Popular products | `GET /products/popular` served from a Redis HASH + SET; write-through on product changes; automatic fallback to the DB if Redis is down |
| Authentication | Signup with BCrypt-hashed passwords, login issuing an opaque 256-bit session token, logout, one active session per user, lazy expiry |
| Cart | One cart per user, merge quantities, absolute quantity update, stock-aware validation |
| Orders | Cart -> order in one transaction: validates and decrements stock, snapshots unit price, clears cart; cancel restores stock |
| Payments | Strategy pattern over `PaymentGateway`; Razorpay Payment Links and Stripe Checkout Sessions; idempotent webhook reconciliation marks the order `PAID` |
| Error handling | Single `@RestControllerAdvice` turning domain exceptions and validation failures into a consistent JSON error body |
| Ops | Spring Boot Actuator `/actuator/health`, H2 console in the default profile, Docker Compose for MySQL + Redis |

---

## Architecture

```
                 +-----------------------------------------------------------+
   HTTP/JSON     |                    Spring Boot application                |
 ------------->  |  Controllers  ->  Services  ->  Repositories (Spring Data) |
                 |      |              |                   |                  |
                 |  AuthInterceptor    |  PaymentGateway    |                  |
                 |  (Bearer token)     |  (Razorpay/Stripe) |                  |
                 +---------------------|-------------------|------------------+
                                       |                   |
                              +--------v------+    +-------v-------+
                              |     Redis     |    |  MySQL / H2   |
                              | popular cache |    |  (JPA tables) |
                              +---------------+    +---------------+
```

Layering is strict and one-directional:

* **Controllers** (`controllers/`) validate input (`jakarta.validation`), read the authenticated
  user from the request, and delegate. No business logic.
* **Services** (`services/`) own all business rules and transactions. `ProductService` is an
  interface with two implementations selected by configuration.
* **Repositories** (`repositories/`) are Spring Data JPA interfaces; custom queries use JPQL.
* **Models** (`models/`) are JPA entities sharing `BaseModel` (id, timestamps, soft-delete flag).
* **Security** (`security/`) is a single `HandlerInterceptor` that resolves the Bearer token to a
  `User` for `/cart/**`, `/orders/**`, `/payments/**` (except the webhook), `/auth/logout`, `/auth/me`.

Diagrams (class, ER, sequence, deployment) live in [`docs/diagrams/`](docs/diagrams/).

---

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.3.5 (Web MVC, Validation, Data JPA, Data Redis, Actuator) |
| Persistence | Hibernate 6 via Spring Data JPA; MySQL 8 in production, H2 (MySQL mode) for local dev and tests |
| Cache | Redis 7 via Lettuce, JSON values (Jackson with JSR-310) |
| Security | `spring-security-crypto` for BCrypt; custom DB-backed session tokens |
| Payments | `razorpay-java` 1.4.6, `stripe-java` 26.0.0 |
| Build | Maven (wrapper included: `./mvnw`) |
| Tests | JUnit 5, Mockito, AssertJ, Spring Boot Test |

---

## Getting started

### Prerequisites

* JDK 21 (`java -version`)
* Nothing else for the default profile. Redis and MySQL are optional (see below).

### Run with zero setup (H2 in-memory)

```bash
git clone https://github.com/shivendrasahab/ecommerce-backend.git
cd ecommerce-backend
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`, creates the schema, and seeds 10 demo products across
5 categories. Without Redis the popular-products endpoint still works; it logs a warning and
serves from the database.

* Health: `curl localhost:8080/actuator/health`
* H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:ecommerce`, user `sa`, empty password)

### Run with MySQL and Redis (Docker Compose)

```bash
docker compose up -d                       # MySQL 8.4 on :3306, Redis 7 on :6379
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

### Build a runnable jar

```bash
./mvnw clean package          # runs the tests too
java -jar target/ecommerce-backend-1.0.0.jar
```

---

## Configuration

All settings live in `src/main/resources/application.properties` and can be overridden with
environment variables or `--key=value` flags.

| Property | Default | Purpose |
|---|---|---|
| `spring.datasource.*` | H2 in-memory | `mysql` profile reads env `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USER`, `MYSQL_PASSWORD` (defaults match docker-compose.yml) |
| `spring.data.redis.host` / `.port` | `localhost` / `6379` | Redis connection (env `REDIS_HOST`, `REDIS_PORT`) |
| `product.source` | `self` | `self` = own DB, `fakestore` = proxy `https://fakestoreapi.com` |
| `fakestore.base-url` | `https://fakestoreapi.com` | Third-party catalogue URL |
| `payment.default-provider` | `RAZORPAY` | Used when a payment request does not name a provider |
| `payment.razorpay.key-id` / `.key-secret` | placeholders | Env `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` |
| `payment.stripe.api-key` | placeholder | Env `STRIPE_API_KEY` |
| `auth.session-ttl-millis` | `172800000` (48 h) | Session lifetime |
| `cache.popular.key` | `popular_products` | Redis hash key (the id SET is `<key>:ids`) |
| `cache.popular.ttl-seconds` | `600` | Cache TTL |
| `cache.popular.keywords` | `iphone,macbook,jordan,rolex` | Title keywords that mark a product as popular |
| `seed.enabled` | `true` | Seed demo catalogue on an empty DB |

Never commit real keys. Export them in the shell or inject them through the platform
(Elastic Beanstalk environment properties, ECS task definition, etc.).

---

## API reference

All request and response bodies are JSON. Protected endpoints require
`Authorization: Bearer <token>` where the token comes from `POST /auth/login`.

### Auth

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/signup` | - | Create account. Body: `name, email, password (>= 8 chars), phoneNumber?` |
| POST | `/auth/login` | - | Returns `{token, email, name, expiresAt}` |
| POST | `/auth/logout` | Bearer | Invalidates the current session |
| GET | `/auth/me` | Bearer | Returns the authenticated user |

### Categories

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/categories` | - | List all |
| GET | `/categories/{id}` | - | Get one |
| POST | `/categories` | - | Create. Body: `name, description?` |

### Products

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/products?page=0&size=10&sortBy=id&direction=asc` | - | Paged listing |
| GET | `/products/popular` | - | Redis-cached popular products |
| GET | `/products/{id}` | - | Get one (404 if soft-deleted) |
| GET | `/products/category/{categoryId}?page=&size=` | - | Products in a category |
| POST | `/products` | - | Create. Body: `title, description?, price, imageUrl?, stock, category` (category by name, created on demand) |
| PUT | `/products/{id}` | - | Full update |
| DELETE | `/products/{id}` | - | Soft delete |

### Search

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/search?q=iphone&page=0&size=10&sortBy=title&direction=asc` | - | `sortBy` must be one of `title`, `price`, `createdAt`; `size` is capped at 50 |

### Cart

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/cart` | Bearer | Current cart (created on first access) |
| POST | `/cart/items` | Bearer | Add. Body: `productId, quantity`. Merges with an existing line |
| PUT | `/cart/items/{productId}?quantity=N` | Bearer | Set absolute quantity; 0 removes the line |
| DELETE | `/cart/items/{productId}` | Bearer | Remove a line |
| DELETE | `/cart` | Bearer | Empty the cart |

### Orders

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/orders` | Bearer | Place order from cart. Body: `shippingAddress`. Status `PENDING_PAYMENT` |
| GET | `/orders?page=0&size=10` | Bearer | Caller's orders, newest first |
| GET | `/orders/{orderId}` | Bearer | One order (404 if it belongs to someone else) |
| POST | `/orders/{orderId}/cancel` | Bearer | Cancel a `PENDING_PAYMENT` order; stock is restored |

### Payments

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/payments/initiate` | Bearer | Body: `orderId, provider? (RAZORPAY|STRIPE), callbackUrl?`. Returns `{paymentId, orderId, provider, paymentLink, amount, status}` |
| GET | `/payments/order/{orderId}` | Bearer | Payment attempts for an order |
| POST | `/payments/webhook` | - | Gateway callback. Body: `providerReferenceId, status (SUCCESS|FAILED)`. Idempotent |

### Error body

Every error, from any layer, has the same shape:

```json
{"status":400,"error":"Bad Request","message":"Only 2 unit(s) of 'Rolex Submariner' available, requested 5","timestamp":"2026-10-06T15:52:23.404911Z"}
```

| HTTP | When |
|---|---|
| 400 | Validation failure, bad request, insufficient stock |
| 401 | Missing/invalid/expired token, wrong credentials |
| 404 | Unknown resource or route |
| 502 | Payment gateway rejected the request |
| 500 | Anything unexpected |

---

## Walkthrough with curl

```bash
BASE=http://localhost:8080

# 1. Browse and search
curl "$BASE/categories"
curl "$BASE/search?q=iphone&sortBy=price&direction=desc"
curl "$BASE/products/popular"

# 2. Sign up and log in
curl -X POST $BASE/auth/signup -H 'Content-Type: application/json' \
  -d '{"name":"Shivendra Singh","email":"shivendra@example.com","password":"Passw0rd!","phoneNumber":"+919999999999"}'
TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"shivendra@example.com","password":"Passw0rd!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

# 3. Cart
curl -X POST $BASE/cart/items -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"productId":1,"quantity":2}'
curl $BASE/cart -H "Authorization: Bearer $TOKEN"

# 4. Order
curl -X POST $BASE/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"shippingAddress":"Bengaluru, KA"}'

# 5. Pay (needs real test keys in RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET or STRIPE_API_KEY)
curl -X POST $BASE/payments/initiate -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"orderId":1,"provider":"RAZORPAY","callbackUrl":"https://example.com/thanks"}'

# 6. Simulate the gateway webhook
curl -X POST $BASE/payments/webhook -H 'Content-Type: application/json' \
  -d '{"providerReferenceId":"plink_xxx","status":"SUCCESS"}'

# 7. Or cancel instead (stock goes back)
curl -X POST $BASE/orders/1/cancel -H "Authorization: Bearer $TOKEN"
```

---

## Testing

```bash
./mvnw test
```

30 tests: a Spring context-load test (profile `test`, H2, no Redis, no seed) plus Mockito unit
tests for `AuthService`, `CartService`, `OrderService`, `SearchService`,
`PopularProductCacheService` and `PaymentService`. They cover password hashing, session expiry,
quantity merging, stock guards, transactional order placement, price snapshotting, cache hit /
miss / outage paths, write-through, provider routing and webhook idempotency.

---

## Benchmark: Redis popular-products cache

`scripts/bench_popular_cache.py` hits `GET /products/popular` 200 times with the cache flushed
before every call (DB path) and 200 times warm (Redis path), on the same JVM after a warm-up.
Measured locally on an Apple Silicon laptop with H2 and Redis 7 on localhost; raw numbers in
`docs/bench-results.json`.

| Path | p50 | p95 | p99 | mean |
|---|---|---|---|---|
| Database (4 `LIKE` queries, cache cold) | 4.52 ms | 6.55 ms | 7.20 ms | 4.77 ms |
| Redis (SMEMBERS + HMGET, cache warm) | 0.99 ms | 1.87 ms | 2.32 ms | 1.12 ms |

Roughly a 4.5x reduction in median latency and 3.5x at p95. The database cost grows with the
number of keywords and catalogue size; the Redis cost does not.

Run it yourself:

```bash
redis-server &                                   # or docker compose up -d redis
java -jar target/ecommerce-backend-1.0.0.jar &
python3 scripts/bench_popular_cache.py
```

---

## Project structure

```
ecommerce-backend
├── pom.xml
├── docker-compose.yml                MySQL 8.4 + Redis 7 for the mysql profile
├── README.md
├── docs/
│   ├── diagrams/                     SVG: architecture, use-case, class, ER, sequence, deployment
│   └── bench-results.json
├── scripts/
│   └── bench_popular_cache.py
└── src
    ├── main
    │   ├── java/com/shivendra/ecommerce
    │   │   ├── EcommerceBackendApplication.java
    │   │   ├── config/        RedisConfig, WebConfig, RestTemplateConfig, PaymentGatewayConfig, DataSeeder
    │   │   ├── controllers/   Auth, Category, Product, Search, Cart, Order, Payment
    │   │   ├── dtos/          request/response objects with validation annotations
    │   │   ├── exceptions/    domain exceptions + GlobalExceptionHandler
    │   │   ├── models/        JPA entities and enums
    │   │   ├── repositories/  Spring Data JPA interfaces
    │   │   ├── security/      AuthInterceptor, CurrentUser
    │   │   └── services/      business logic; services/payment holds the gateway strategy
    │   └── resources
    │       ├── application.properties
    │       └── application-mysql.properties
    └── test
        ├── java/com/shivendra/ecommerce/...   unit tests + context-load test
        └── resources/application-test.properties
```

---

## Deployment on AWS

The intended production topology (detailed in the project report):

* **VPC** with public subnets (load balancer) and private subnets (application, database, cache).
* **Elastic Beanstalk** (Corretto 21 platform) running the jar behind an Application Load
  Balancer, health check on `/actuator/health`, auto scaling 2-6 instances.
* **Amazon RDS for MySQL 8** (Multi-AZ) in the private subnet; connection injected as
  environment properties `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USER`, `MYSQL_PASSWORD`
  with `SPRING_PROFILES_ACTIVE=mysql`.
* **Amazon ElastiCache for Redis** in the private subnet; `REDIS_HOST` points at the primary
  endpoint.
* **Security groups**: ALB accepts 443 from the internet; app accepts 8080 only from the ALB SG;
  RDS accepts 3306 and ElastiCache 6379 only from the app SG.
* Payment gateway secrets from AWS Secrets Manager or Beanstalk environment properties.

Deploy with the EB CLI:

```bash
./mvnw clean package -DskipTests
eb init ecommerce-backend --platform "Corretto 21" --region ap-south-1
eb create ecommerce-prod --instance_type t3.small --envvars SPRING_PROFILES_ACTIVE=mysql,REDIS_HOST=...
eb deploy
```

---

## Design decisions

* **Opaque DB-backed session tokens instead of JWT.** Logout and forced expiry are a row update;
  no key rotation or token revocation lists. Fine for one backend, revisit if several services
  need to validate tokens independently.
* **Soft delete for products.** Historical `order_items` keep a valid foreign key and the catalogue
  can hide an item without losing its sales history.
* **Price snapshot on `OrderItem`.** A later price change never alters what a customer was charged.
* **Strategy pattern for gateways.** `PaymentService` discovers every `PaymentGateway` bean and
  indexes it by `PaymentProvider`; adding PayPal means one new class.
* **Cache as accelerator, not dependency.** Every Redis call in `PopularProductCacheService` is
  wrapped; failures log and fall back to the DB, and Redis is excluded from the health check so a
  cache outage never takes the service out of the load balancer.
* **Allow-listed sorting.** `SearchService` rejects any `sortBy` outside `title|price|createdAt`,
  so callers cannot probe or sort by arbitrary columns.

## License

MIT
