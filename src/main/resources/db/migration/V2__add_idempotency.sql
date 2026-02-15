ALTER TABLE events ADD COLUMN idempotency_key VARCHAR(255);
CREATE INDEX idx_events_idempotency ON events(idempotency_key, destination_id);
