CREATE TABLE catalog_batches (
  id VARCHAR(64) PRIMARY KEY,
  archive_sha256 VARCHAR(64) NOT NULL,
  imported_at TIMESTAMP NOT NULL,
  status VARCHAR(24) NOT NULL,
  summary TEXT NOT NULL
);
CREATE TABLE catalog_brands (
  id VARCHAR(32) PRIMARY KEY,
  name VARCHAR(200) NOT NULL
);
CREATE TABLE catalog_products (
  id VARCHAR(32) PRIMARY KEY,
  brand_id VARCHAR(32) NOT NULL,
  category VARCHAR(40) NOT NULL,
  name VARCHAR(400) NOT NULL,
  featured BOOLEAN NOT NULL,
  state VARCHAR(24) NOT NULL DEFAULT 'PREVIEW',
  main_image_id VARCHAR(32),
  batch_id VARCHAR(64) NOT NULL,
  raw_json TEXT NOT NULL,
  FOREIGN KEY(brand_id) REFERENCES catalog_brands(id),
  FOREIGN KEY(batch_id) REFERENCES catalog_batches(id)
);
CREATE INDEX idx_catalog_category ON catalog_products(category, id);
CREATE INDEX idx_catalog_brand ON catalog_products(brand_id, id);
CREATE TABLE catalog_colorways (
  id VARCHAR(32) PRIMARY KEY,
  product_id VARCHAR(32) NOT NULL,
  name VARCHAR(400) NOT NULL,
  color_explicit BOOLEAN NOT NULL,
  style_code VARCHAR(200) NOT NULL,
  main_image_id VARCHAR(32),
  raw_json TEXT NOT NULL,
  FOREIGN KEY(product_id) REFERENCES catalog_products(id)
);
CREATE INDEX idx_colorways_product ON catalog_colorways(product_id);
CREATE TABLE catalog_skus (
  id VARCHAR(32) PRIMARY KEY,
  product_id VARCHAR(32) NOT NULL,
  colorway_id VARCHAR(32) NOT NULL,
  specs_json TEXT NOT NULL,
  source_sku VARCHAR(300) NOT NULL,
  source_variant_id VARCHAR(100) NOT NULL,
  source_url TEXT NOT NULL,
  source_available BOOLEAN NOT NULL,
  source_price_amount DECIMAL(18,4),
  source_price_currency VARCHAR(12),
  sale_price_cents BIGINT,
  sale_currency VARCHAR(3),
  own_stock INTEGER,
  main_image_id VARCHAR(32),
  raw_json TEXT NOT NULL,
  FOREIGN KEY(product_id) REFERENCES catalog_products(id),
  FOREIGN KEY(colorway_id) REFERENCES catalog_colorways(id),
  CHECK(sale_price_cents IS NULL OR sale_price_cents > 0),
  CHECK(own_stock IS NULL OR own_stock >= 0)
);
CREATE INDEX idx_catalog_skus_product ON catalog_skus(product_id, colorway_id);
CREATE TABLE catalog_images (
  id VARCHAR(32) PRIMARY KEY,
  product_id VARCHAR(32) NOT NULL,
  colorway_id VARCHAR(32),
  sha256 VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  object_key VARCHAR(180),
  thumb_key VARCHAR(180),
  detail_key VARCHAR(180),
  source_position INTEGER NOT NULL,
  raw_json TEXT NOT NULL,
  FOREIGN KEY(product_id) REFERENCES catalog_products(id)
);
CREATE INDEX idx_catalog_images_product ON catalog_images(product_id, colorway_id, source_position);
CREATE TABLE catalog_sku_images (
  sku_id VARCHAR(32) NOT NULL,
  image_id VARCHAR(32) NOT NULL,
  mapping_method VARCHAR(150) NOT NULL,
  review_required BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY(sku_id,image_id),
  FOREIGN KEY(sku_id) REFERENCES catalog_skus(id),
  FOREIGN KEY(image_id) REFERENCES catalog_images(id)
);
CREATE TABLE catalog_issues (
  id VARCHAR(100) PRIMARY KEY,
  batch_id VARCHAR(64) NOT NULL,
  kind VARCHAR(40) NOT NULL,
  entity_id VARCHAR(32) NOT NULL,
  detail TEXT NOT NULL,
  FOREIGN KEY(batch_id) REFERENCES catalog_batches(id)
);
CREATE TABLE catalog_models (
  id VARCHAR(64) PRIMARY KEY,
  product_id VARCHAR(32) NOT NULL,
  colorway_id VARCHAR(32) NOT NULL UNIQUE,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_ASSET',
  object_key VARCHAR(180),
  sha256 VARCHAR(64),
  byte_size BIGINT,
  license_name VARCHAR(200),
  license_url TEXT,
  source_url TEXT,
  copyright_name VARCHAR(300),
  reviewed BOOLEAN NOT NULL DEFAULT FALSE,
  FOREIGN KEY(product_id) REFERENCES catalog_products(id),
  FOREIGN KEY(colorway_id) REFERENCES catalog_colorways(id)
);
CREATE TABLE catalog_meta (
  id VARCHAR(32) PRIMARY KEY,
  value_text VARCHAR(100) NOT NULL
);
INSERT INTO catalog_meta(id,value_text) VALUES('revision','0');
