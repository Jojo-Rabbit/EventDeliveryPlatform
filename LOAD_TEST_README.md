# Load Test Guide

## Overview
The `load_test.py` script tests the Event Delivery Platform's key features:
- ✅ Destination creation with rate limiting
- ✅ Idempotency handling
- ✅ Rate limiting enforcement
- ✅ Event delivery with HMAC signatures

## Prerequisites

1. **Application Running**: Make sure the Spring Boot app is running
   ```bash
   mvn spring-boot:run
   # OR
   docker-compose up
   ```

2. **Webhook Endpoint**: Get a webhook URL from [webhook.site](https://webhook.site)
   - Visit https://webhook.site
   - Copy your unique URL (e.g., `https://webhook.site/12345678-1234-1234-1234-123456789abc`)
   - Update `WEBHOOK_URL` in `load_test.py`

## Running the Test

```bash
python3 load_test.py
```

## What the Test Does

### 1. **Creates a Test Destination**
- Name: `LoadTest-{timestamp}`
- Rate Limit: 5 requests/second
- Signing Secret: `test-secret-key-123`

### 2. **Tests Idempotency**
- Sends same request twice with identical `Idempotency-Key`
- Verifies both requests return the same Event ID
- Confirms duplicate detection works

### 3. **Tests Rate Limiting**
- Sends burst of 15 events rapidly
- Events are queued immediately (202 ACCEPTED)
- Rate limiting is enforced during delivery (check logs)
- Should see ~5 events/sec delivery rate

### 4. **Sends Batch Events**
- Sends 20 events total
- Every 5th event uses a duplicate idempotency key
- Tests normal flow with some duplicates mixed in

## Expected Output

```
============================================================
🚀 Event Delivery Platform - Load Test
============================================================
Target: http://localhost:8080/api
Webhook: https://webhook.site/your-uuid-here

📍 Creating destination 'LoadTest-1234567890' with 5 RPS limit...
✅ Destination created successfully (ID: abc-123-def)

🔄 Testing idempotency...
✅ First request accepted (Event ID: event-id-1)
✅ Idempotency working! Same event ID returned: event-id-1

⚡ Testing rate limiting (sending 15 events rapidly)...
   Expected: ~5 events/sec throughput
✅ Sent 15/15 events in 0.45s
   Note: Events are queued immediately, rate limiting happens during delivery
   Check logs to verify delivery rate is ~5 req/sec

📤 Sending 20 events (with some duplicates for idempotency testing)...
   Progress: 0/20 events sent...
   Progress: 10/20 events sent...
✅ Completed: 20/20 events sent in 0.82s
   Duplicates sent: 4

============================================================
✅ Load test completed!
============================================================
```

## What to Verify

### 1. Application Logs
Look for these messages:
```
Duplicate request detected for key idemp:...
Rate limit exceeded for destination ... Re-queuing event ...
Event ... delivered successfully
```

### 2. Webhook.site Dashboard
- Events should arrive at ~5 per second
- Each request should have:
  - Header: `X-Edp-Signature: sha256=...`
  - Header: `Content-Type: application/json`
  - Body: Your event payload

### 3. Database (Optional)
Query the database to verify:
```sql
-- Check events
SELECT id, status, created_at FROM events ORDER BY created_at DESC LIMIT 20;

-- Check delivery attempts
SELECT event_id, success, response_code, duration_ms 
FROM delivery_attempts 
ORDER BY attempted_at DESC LIMIT 20;
```

## Customization

Edit these variables in `load_test.py`:

```python
BASE_URL = "http://localhost:8080/api"  # Your API URL
WEBHOOK_URL = "https://webhook.site/..." # Your webhook URL
RPS_LIMIT = 5                            # Rate limit (req/sec)
```

## Troubleshooting

### Connection Error
```
❌ Connection error: [Errno 61] Connection refused
```
**Solution**: Make sure the application is running on port 8080

### URL Validation Failed
```
❌ Failed to create destination:
   Status: 400
   Body: {"url": "must be a valid URL"}
```
**Solution**: Update `WEBHOOK_URL` with a valid URL (not the placeholder)

### Events Not Arriving at Webhook
**Check**:
1. Application logs for delivery errors
2. Network connectivity
3. Webhook URL is correct and accessible

## Advanced Testing

### Test Higher Rate Limits
```python
RPS_LIMIT = 10  # Increase to 10 req/sec
test_rate_limiting(dest_id, burst_size=50)  # Larger burst
```

### Test Replay Functionality
```bash
curl -X POST http://localhost:8080/api/replay \
  -H "Content-Type: application/json" \
  -d '{
    "destinationId": "your-dest-id",
    "status": "FAILED",
    "startTime": "2026-02-15T00:00:00"
  }'
```

## Monitoring Commands

```bash
# Watch application logs
docker-compose logs -f

# Watch only dispatcher logs
docker-compose logs -f | grep DispatcherWorker

# Check Kafka topics
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic events.primary \
  --from-beginning

# Monitor Redis
docker exec -it redis redis-cli
> KEYS idemp:*
> GET idemp:destination-id:your-key
```
