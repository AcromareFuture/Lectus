CREATE TABLE account_locks (id VARCHAR(36) PRIMARY KEY);
CREATE TABLE products (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  english_name VARCHAR(200) NOT NULL,
  brand VARCHAR(100) NOT NULL,
  category VARCHAR(50) NOT NULL,
  price_cents BIGINT NOT NULL CHECK (price_cents > 0),
  image VARCHAR(150) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  tag VARCHAR(50) NOT NULL,
  popularity INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE product_skus (
  id VARCHAR(36) PRIMARY KEY,
  product_id VARCHAR(64) NOT NULL REFERENCES products(id),
  size VARCHAR(40) NOT NULL,
  color VARCHAR(80) NOT NULL,
  stock INTEGER NOT NULL CHECK (stock >= 0),
  UNIQUE(product_id, size, color)
);
CREATE TABLE cart_items (
  account_id VARCHAR(36) NOT NULL,
  sku_id VARCHAR(36) NOT NULL REFERENCES product_skus(id),
  quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
  checked BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY(account_id, sku_id)
);
CREATE TABLE addresses (
  id VARCHAR(36) PRIMARY KEY,
  account_id VARCHAR(36) NOT NULL,
  recipient VARCHAR(80) NOT NULL,
  phone VARCHAR(30) NOT NULL,
  region VARCHAR(200) NOT NULL,
  detail VARCHAR(300) NOT NULL,
  is_default BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_addresses_account ON addresses(account_id);
CREATE TABLE shop_orders (
  id VARCHAR(36) PRIMARY KEY,
  account_id VARCHAR(36) NOT NULL,
  status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'CANCELLED')),
  total_cents BIGINT NOT NULL CHECK (total_cents >= 0),
  address_snapshot VARCHAR(4000) NOT NULL,
  idempotency_key VARCHAR(80) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  UNIQUE(account_id, idempotency_key)
);
CREATE INDEX idx_orders_account ON shop_orders(account_id, created_at);
CREATE TABLE order_items (
  order_id VARCHAR(36) NOT NULL REFERENCES shop_orders(id),
  sku_id VARCHAR(36) NOT NULL REFERENCES product_skus(id),
  product_id VARCHAR(64) NOT NULL,
  product_name VARCHAR(200) NOT NULL,
  image VARCHAR(150) NOT NULL,
  size VARCHAR(40) NOT NULL,
  color VARCHAR(80) NOT NULL,
  unit_price_cents BIGINT NOT NULL CHECK (unit_price_cents > 0),
  quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
  PRIMARY KEY(order_id, sku_id)
);
