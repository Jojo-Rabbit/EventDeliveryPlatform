CREATE TABLE destinations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(255) NOT NULL,
    http_method VARCHAR(50) NOT NULL,
    headers TEXT,
    signing_secret VARCHAR(255),
    rate_limit_rps INTEGER DEFAULT 10,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    destination_id UUID REFERENCES destinations(id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    event_id UUID REFERENCES events(id),
    response_code INTEGER,
    response_body TEXT,
    success BOOLEAN,
    duration_ms BIGINT,
    attempted_at TIMESTAMP
);
