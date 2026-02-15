# Event Delivery Platform

A production-ready webhook delivery system built to handle high-throughput event processing with guaranteed delivery, rate limiting, and idempotency support.

## Overview

This platform acts as a reliable intermediary between your internal systems and external webhooks. Think of it as your own Stripe/Twilio-style webhook infrastructure - you send us events, we guarantee delivery to your configured endpoints with proper retry logic, rate limiting, and security.

**Key capabilities:**
- At-least-once delivery guarantee
- Configurable rate limiting per destination
- Idempotency to prevent duplicate processing
- HMAC signature verification
- Automatic retry with exponential backoff
- Dead letter queue for permanently failed events
- Event replay functionality

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Language** | Java | 21 (LTS) |
| **Framework** | Spring Boot | 3.4.2 |
| **Database** | PostgreSQL | 16 |
| **Message Broker** | Apache Kafka | 7.5.0 |
| **Cache/Rate Limiting** | Redis | Alpine |
| **Migration Tool** | Flyway | (via Spring Boot) |
| **Rate Limiting** | Bucket4j | 7.6.0 |
| **Build Tool** | Maven | 3.x |
| **Containerization** | Docker & Docker Compose | - |

## Architecture

The system follows an event-driven architecture with clear separation of concerns:

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ POST /api/events
       ▼
┌─────────────────────┐
│  Ingestion API      │ ◄── Idempotency Check (Redis)
│  (Spring Boot)      │
└──────┬──────────────┘
       │ Save event
       ▼
┌─────────────────────┐
│   PostgreSQL        │
│   (Event Store)     │
└─────────────────────┘
       │
       │ Publish
       ▼
┌─────────────────────┐
│      Kafka          │
│  (events.primary)   │
└──────┬──────────────┘
       │ Consume
       ▼
┌─────────────────────┐
│ Dispatcher Worker   │ ◄── Rate Limiter (Bucket4j)
│  (Spring Boot)      │
└──────┬──────────────┘
       │ HTTP POST (signed)
       ▼
┌─────────────────────┐
│  External Webhook   │
└─────────────────────┘
```

### Data Flow

**Happy Path:**
1. Client sends event to `/api/events` with optional `Idempotency-Key` header
2. API validates request and checks for duplicate (Redis)
3. Event saved to PostgreSQL with status `RECEIVED`
4. Event published to Kafka topic `events.primary`
5. API returns `202 Accepted` immediately
6. Dispatcher worker consumes from Kafka
7. Worker checks rate limit for destination
8. Worker signs payload with HMAC-SHA256
9. Worker sends HTTP POST to configured webhook URL
10. On success, event status updated to `DELIVERED`

**Failure Handling:**
- 5xx errors or timeouts → Retry with exponential backoff (up to 5 attempts)
- Rate limit exceeded → Re-queue with delay
- Max retries exceeded → Move to Dead Letter Queue (DLQ)
- All attempts logged in `delivery_attempts` table

## Features

### Core Functionality

- **Event Ingestion API**
  - REST endpoint to receive events
  - Payload validation
  - Idempotency key support
  - Immediate acknowledgment (202 Accepted)

- **Reliable Delivery**
  - At-least-once delivery guarantee
  - Automatic retry with exponential backoff
  - Configurable retry policies per destination
  - Dead letter queue for failed events

- **Rate Limiting**
  - Per-destination rate limits (requests/second)
  - Token bucket algorithm via Bucket4j
  - Non-blocking implementation (events re-queued, not dropped)

- **Security**
  - HMAC-SHA256 payload signing
  - Signature sent in `X-Edp-Signature` header
  - Per-destination signing secrets

- **Idempotency**
  - Redis-backed idempotency checks
  - 24-hour key retention
  - Prevents duplicate event processing

- **Event Replay**
  - Replay failed events by destination
  - Filter by status and time range
  - Useful for recovering from downstream outages

### Monitoring & Observability

- Detailed delivery attempt logging
- Event status tracking (RECEIVED → PROCESSING → DELIVERED/FAILED)
- Response code and duration metrics
- Kafka UI for message inspection (port 8090)

## Database Schema

**events**
- `id` (UUID, PK)
- `destination_id` (UUID, FK)
- `payload` (TEXT)
- `status` (ENUM: RECEIVED, PROCESSING, DELIVERED, FAILED, PERMANENTLY_FAILED)
- `idempotency_key` (VARCHAR)
- `created_at`, `updated_at`

**destinations**
- `id` (UUID, PK)
- `name` (VARCHAR)
- `url` (VARCHAR)
- `http_method` (VARCHAR)
- `headers` (TEXT, JSON format)
- `signing_secret` (VARCHAR)
- `rate_limit_rps` (INTEGER)
- `created_at`

**delivery_attempts**
- `id` (UUID, PK)
- `event_id` (UUID, FK)
- `response_code` (INTEGER)
- `response_body` (TEXT)
- `success` (BOOLEAN)
- `duration_ms` (BIGINT)
- `attempted_at`

## Project Setup

### Prerequisites

- Java 21 or higher
- Maven 3.x
- Docker & Docker Compose
- Python 3.x (for load testing)

### Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd EventDeliveryPlatform
   ```

2. **Start infrastructure services**
   ```bash
   docker-compose up -d
   ```
   
   This starts:
   - PostgreSQL (port 5434)
   - Kafka + Zookeeper (port 9092)
   - Kafka UI (port 8090)
   - Redis (port 6379)

3. **Run the application**
   ```bash
   mvn clean spring-boot:run
   ```
   
   The API will be available at `http://localhost:8080`

4. **Verify setup**
   ```bash
   # Check if all containers are running
   docker-compose ps
   
   # Check application health
   curl http://localhost:8080/actuator/health
   ```

### Configuration

Edit `src/main/resources/application.yml` to customize:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5434/event_delivery_db
    username: user
    password: password
  
  kafka:
    bootstrap-servers: localhost:9092
  
  data:
    redis:
      host: localhost
      port: 6379

server:
  port: 8080
```

### Database Migrations

Flyway handles schema migrations automatically on startup. Migration files are in `src/main/resources/db/migration/`.

To create a new migration:
```bash
# Create file: V{version}__{description}.sql
# Example: V003__add_replay_index.sql
```

## API Usage

### Create a Destination

```bash
curl -X POST http://localhost:8080/api/destinations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Webhook",
    "url": "https://webhook.site/your-uuid",
    "httpMethod": "POST",
    "rateLimitRps": 10,
    "signingSecret": "your-secret-key"
  }'
```

### Send an Event

```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-123" \
  -d '{
    "destinationId": "destination-uuid-here",
    "payload": "{\"order_id\": \"12345\", \"status\": \"completed\"}"
  }'
```

### Get Event Status

```bash
curl http://localhost:8080/api/events/{event-id}
```

### Replay Failed Events

```bash
curl -X POST http://localhost:8080/api/replay \
  -H "Content-Type: application/json" \
  -d '{
    "destinationId": "destination-uuid",
    "status": "FAILED",
    "startTime": "2026-02-15T00:00:00"
  }'
```

## Load Testing

A Python-based load test script is included to verify the system's functionality.

### Setup

1. **Get a webhook URL** from [webhook.site](https://webhook.site)

2. **Update the script**
   ```python
   # In load_test.py
   WEBHOOK_URL = "https://webhook.site/your-actual-uuid"
   ```

3. **Run the test**
   ```bash
   python3 load_test.py
   ```

### What the Load Test Does

- Creates a test destination with 5 req/sec rate limit
- Tests idempotency by sending duplicate requests
- Sends burst of events to verify rate limiting
- Sends batch of 20 events with some duplicates
- Provides detailed output and verification steps

### Expected Results

```
✅ Destination created successfully
✅ Idempotency working! Same event ID returned
✅ Sent 15/15 events in 0.45s
✅ Completed: 20/20 events sent in 0.82s
```

Check the application logs for:
- `Duplicate request detected` (idempotency working)
- `Rate limit exceeded` (rate limiting active)
- `Event delivered successfully` (successful deliveries)

For detailed instructions, see [LOAD_TEST_README.md](LOAD_TEST_README.md).

## Monitoring

### Application Logs

```bash
# Watch all logs
docker-compose logs -f

# Filter by service
docker-compose logs -f postgres
docker-compose logs -f kafka

# Watch application logs (if running via Maven)
# Logs are printed to console with DEBUG level for com.eventdelivery package
```

### Kafka UI

Access Kafka UI at `http://localhost:8090` to:
- View topics and messages
- Monitor consumer lag
- Inspect message payloads
- Check partition distribution

### Redis Monitoring

```bash
# Connect to Redis CLI
docker exec -it edp-redis redis-cli

# Check idempotency keys
KEYS idemp:*

# View specific key
GET idemp:destination-id:your-key

# Check rate limiter buckets (if using Redis for rate limiting)
KEYS ratelimit:*
```

### Database Queries

```sql
-- Check recent events
SELECT id, status, created_at 
FROM events 
ORDER BY created_at DESC 
LIMIT 10;

-- Check delivery attempts
SELECT e.id, da.response_code, da.success, da.duration_ms, da.attempted_at
FROM events e
JOIN delivery_attempts da ON e.id = da.event_id
ORDER BY da.attempted_at DESC
LIMIT 20;

-- Check failed events
SELECT id, payload, status, created_at
FROM events
WHERE status IN ('FAILED', 'PERMANENTLY_FAILED')
ORDER BY created_at DESC;
```

## High-Level Design

For a detailed architectural overview, see [HLD.md](HLD.md), which covers:
- Multi-region deployment strategy
- Failure handling and retry logic
- Database replication approach
- Scalability considerations
- Technology choices and rationale

## Future Enhancements

### Short Term
- [ ] Add authentication/API keys for ingestion endpoint
- [ ] Implement circuit breaker for consistently failing destinations
- [ ] Add metrics export (Prometheus/Grafana)
- [ ] Support for custom headers per destination
- [ ] Batch event ingestion API

### Medium Term
- [ ] Multi-region active-active deployment
- [ ] Event filtering and transformation rules
- [ ] Webhook endpoint verification (challenge-response)
- [ ] Admin UI for managing destinations
- [ ] Delivery analytics dashboard

### Long Term
- [ ] Support for additional protocols (gRPC, GraphQL)
- [ ] Event schema validation
- [ ] Custom retry policies per destination
- [ ] Webhook endpoint health monitoring
- [ ] Multi-tenancy support
- [ ] Event archival to S3/object storage

## Troubleshooting

### Application won't start

**Issue**: Connection refused to PostgreSQL
```
Solution: Ensure Docker containers are running
docker-compose up -d
```

**Issue**: Port already in use
```
Solution: Change port in application.yml or stop conflicting service
lsof -ti:8080 | xargs kill -9
```

### Events not being delivered

**Check**:
1. Kafka is running: `docker-compose ps kafka`
2. Worker is consuming: Check logs for "Consuming event from topic"
3. Destination URL is reachable: Test with `curl`
4. Rate limit not exceeded: Check logs for "Rate limit exceeded"

### Idempotency not working

**Check**:
1. Redis is running: `docker exec -it edp-redis redis-cli ping`
2. Idempotency-Key header is being sent
3. Key hasn't expired (24-hour TTL)

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Submit a pull request

## License

This project is licensed under the MIT License.

---

**Built with ☕ and Spring Boot**
