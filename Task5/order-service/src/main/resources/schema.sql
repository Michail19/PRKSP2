-- Идемпотентная схема данных учебной АС доставки.
CREATE TABLE IF NOT EXISTS darkstores (
    id BIGINT PRIMARY KEY,
    address TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS goods (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0)
);
CREATE TABLE IF NOT EXISTS delivery_orders (
    id BIGINT PRIMARY KEY,
    darkstore_id BIGINT NOT NULL REFERENCES darkstores(id),
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL
);
CREATE TABLE IF NOT EXISTS order_items (
    order_id BIGINT NOT NULL REFERENCES delivery_orders(id),
    good_id BIGINT NOT NULL REFERENCES goods(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, good_id)
);
CREATE INDEX IF NOT EXISTS idx_delivery_orders_darkstore ON delivery_orders(darkstore_id);
