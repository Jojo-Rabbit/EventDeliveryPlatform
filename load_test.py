import urllib.request
import urllib.error
import json
import time
import uuid
import sys

# Configuration
BASE_URL = "http://localhost:8080/api"
# Using a mock webhook service - replace with your own webhook.site URL for real testing
WEBHOOK_URL = "https://webhook.site/unique-uuid-here"
DESTINATION_NAME = f"LoadTest-{int(time.time())}"
RPS_LIMIT = 5

def make_request(url, method="POST", data=None, headers=None):
    """Make HTTP request with proper error handling"""
    if headers is None:
        headers = {}
    
    if data is not None:
        json_data = json.dumps(data).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    else:
        json_data = None

    req = urllib.request.Request(url, data=json_data, headers=headers, method=method)
    
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            response_body = response.read().decode('utf-8')
            try:
                body = json.loads(response_body)
            except json.JSONDecodeError:
                body = response_body
            return {
                "status_code": response.getcode(),
                "body": body
            }
    except urllib.error.HTTPError as e:
        error_body = e.read().decode('utf-8')
        try:
            error_json = json.loads(error_body)
        except json.JSONDecodeError:
            error_json = error_body
        return {
            "status_code": e.code,
            "body": error_json
        }
    except urllib.error.URLError as e:
        print(f"❌ Connection error: {e}")
        print(f"   Make sure the application is running at {BASE_URL}")
        return None
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        return None

def create_destination():
    """Create a test destination with rate limiting"""
    print(f"\n📍 Creating destination '{DESTINATION_NAME}' with {RPS_LIMIT} RPS limit...")
    payload = {
        "name": DESTINATION_NAME,
        "url": WEBHOOK_URL,
        "httpMethod": "POST",
        "rateLimitRps": RPS_LIMIT,
        "signingSecret": "test-secret-key-123"
    }
    response = make_request(f"{BASE_URL}/destinations", method="POST", data=payload)
    
    if response and response["status_code"] == 200:
        dest_id = response["body"]['id']
        print(f"✅ Destination created successfully (ID: {dest_id})")
        return dest_id
    else:
        print(f"❌ Failed to create destination:")
        if response:
            print(f"   Status: {response['status_code']}")
            print(f"   Body: {response['body']}")
        sys.exit(1)

def test_idempotency(destination_id):
    """Test idempotency by sending duplicate requests"""
    print(f"\n🔄 Testing idempotency...")
    
    idempotency_key = f"test-idemp-{uuid.uuid4()}"
    payload = {
        "destinationId": destination_id,
        "payload": '{"test": "idempotency", "timestamp": ' + str(time.time()) + '}'
    }
    headers = {"Idempotency-Key": idempotency_key}
    
    # First request
    response1 = make_request(f"{BASE_URL}/events", method="POST", data=payload, headers=headers)
    if not response1 or response1["status_code"] != 202:
        print(f"❌ First request failed: {response1}")
        return False
    
    event_id_1 = response1["body"]["id"]
    print(f"✅ First request accepted (Event ID: {event_id_1})")
    
    # Duplicate request with same idempotency key
    time.sleep(0.1)
    response2 = make_request(f"{BASE_URL}/events", method="POST", data=payload, headers=headers)
    if not response2 or response2["status_code"] != 202:
        print(f"❌ Duplicate request failed: {response2}")
        return False
    
    event_id_2 = response2["body"]["id"]
    
    if event_id_1 == event_id_2:
        print(f"✅ Idempotency working! Same event ID returned: {event_id_1}")
        return True
    else:
        print(f"❌ Idempotency failed! Different IDs: {event_id_1} vs {event_id_2}")
        return False

def test_rate_limiting(destination_id, burst_size=15):
    """Test rate limiting by sending a burst of events"""
    print(f"\n⚡ Testing rate limiting (sending {burst_size} events rapidly)...")
    print(f"   Expected: ~{RPS_LIMIT} events/sec throughput")
    
    start_time = time.time()
    success_count = 0
    
    for i in range(burst_size):
        payload = {
            "destinationId": destination_id,
            "payload": f'{{"burst_test": true, "index": {i}, "timestamp": {time.time()}}}'
        }
        headers = {"Idempotency-Key": f"burst-{uuid.uuid4()}"}
        
        response = make_request(f"{BASE_URL}/events", method="POST", data=payload, headers=headers)
        
        if response and response["status_code"] == 202:
            success_count += 1
        else:
            print(f"   Event {i} failed: {response}")
    
    elapsed = time.time() - start_time
    print(f"✅ Sent {success_count}/{burst_size} events in {elapsed:.2f}s")
    print(f"   Note: Events are queued immediately, rate limiting happens during delivery")
    print(f"   Check logs to verify delivery rate is ~{RPS_LIMIT} req/sec")

def send_events(destination_id, count=20):
    """Send a batch of events with some duplicates"""
    print(f"\n📤 Sending {count} events (with some duplicates for idempotency testing)...")
    start_time = time.time()
    
    success_count = 0
    duplicate_count = 0
    
    for i in range(count):
        payload = {
            "destinationId": destination_id,
            "payload": f'{{"event_index": {i}, "timestamp": {time.time()}, "data": "test-payload"}}'
        }
        
        # Every 5th event gets a repeated idempotency key
        if i > 0 and i % 5 == 0:
            headers = {"Idempotency-Key": f"repeated-key-{i}"}
            # Send duplicate first
            dup_response = make_request(f"{BASE_URL}/events", method="POST", data=payload, headers=headers)
            if dup_response and dup_response["status_code"] == 202:
                duplicate_count += 1
        else:
            headers = {"Idempotency-Key": f"unique-{uuid.uuid4()}"}

        response = make_request(f"{BASE_URL}/events", method="POST", data=payload, headers=headers)
        
        if response and response["status_code"] == 202:
            success_count += 1
            if i % 10 == 0:
                print(f"   Progress: {i}/{count} events sent...")
        else:
            print(f"   ❌ Event {i} failed: {response}")
            
    elapsed = time.time() - start_time
    print(f"✅ Completed: {success_count}/{count} events sent in {elapsed:.2f}s")
    print(f"   Duplicates sent: {duplicate_count}")

def main():
    print("=" * 60)
    print("🚀 Event Delivery Platform - Load Test")
    print("=" * 60)
    print(f"Target: {BASE_URL}")
    print(f"Webhook: {WEBHOOK_URL}")
    
    # Validate webhook URL
    if "YOUR_UUID" in WEBHOOK_URL or "unique-uuid-here" in WEBHOOK_URL:
        print("\n⚠️  WARNING: Using placeholder webhook URL")
        print("   For real testing, replace WEBHOOK_URL with a valid webhook.site URL")
        print("   Example: https://webhook.site/12345678-1234-1234-1234-123456789abc")
        response = input("\n   Continue anyway? (y/n): ")
        if response.lower() != 'y':
            print("Exiting...")
            sys.exit(0)
    
    # Create destination
    dest_id = create_destination()
    
    # Run tests
    test_idempotency(dest_id)
    test_rate_limiting(dest_id, burst_size=15)
    send_events(dest_id, count=20)
    
    # Summary
    print("\n" + "=" * 60)
    print("✅ Load test completed!")
    print("=" * 60)
    print("\n📊 What to check:")
    print("   1. Application logs - look for:")
    print("      • 'Duplicate request detected' messages (idempotency)")
    print("      • 'Rate limit exceeded' warnings")
    print("      • Event delivery success/failure logs")
    print(f"   2. Webhook endpoint ({WEBHOOK_URL}):")
    print("      • Events arriving at ~5 per second")
    print("      • 'X-Edp-Signature' header present")
    print("      • Payload matches sent data")
    print("   3. Database - verify:")
    print("      • Events table has all events")
    print("      • Delivery attempts recorded")
    print("      • Event statuses (RECEIVED → PROCESSING → DELIVERED)")
    print("\n💡 Tip: Run 'docker-compose logs -f' to watch real-time logs")
    print("=" * 60)

if __name__ == "__main__":
    main()
