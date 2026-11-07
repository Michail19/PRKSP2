CREATE TABLE IF NOT EXISTS orders (
 id BIGSERIAL PRIMARY KEY,
 product_id BIGINT NOT NULL,
 quantity INT NOT NULL CHECK (quantity > 0),
 unit_price_kopecks BIGINT NOT NULL CHECK (unit_price_kopecks > 0),
 total_kopecks BIGINT NOT NULL CHECK (total_kopecks > 0),
 status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS outbox_events (
 event_id UUID PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES orders(id),
 event_type VARCHAR(80) NOT NULL,
 payload TEXT NOT NULL,
 published BOOLEAN NOT NULL DEFAULT false,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 published_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events(published,created_at);
