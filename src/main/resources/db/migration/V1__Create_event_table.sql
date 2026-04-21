CREATE TABLE IF NOT EXISTS events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount NUMERIC(10,2),
    currency VARCHAR(10),
    timestamp TIMESTAMP WITH TIME ZONE,
    wiremock_response TEXT,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Добавляем индексы для удобства поиска (полезно в будущем)
CREATE INDEX IF NOT EXISTS idx_events_event_id ON events(event_id);
CREATE INDEX IF NOT EXISTS idx_events_user_id ON events(user_id);