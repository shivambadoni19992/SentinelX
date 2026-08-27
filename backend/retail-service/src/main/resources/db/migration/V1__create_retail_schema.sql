-- SentinelX retail-service · schema: retail
CREATE SCHEMA IF NOT EXISTS retail;

CREATE TABLE retail.products (
    id          uuid          PRIMARY KEY,
    sku         varchar(128)  NOT NULL,
    name        varchar(255)  NOT NULL,
    description text,
    category    varchar(128),
    price       numeric(19,4) NOT NULL CHECK (price >= 0),
    currency    varchar(3)    NOT NULL DEFAULT 'USD',
    stock       int           NOT NULL DEFAULT 0,
    active      boolean       NOT NULL DEFAULT true,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_products_sku ON retail.products (sku);
CREATE INDEX        idx_products_category ON retail.products (category);

CREATE TABLE retail.orders (
    id           uuid          PRIMARY KEY,
    user_id      uuid          NOT NULL,
    status       varchar(64)   NOT NULL DEFAULT 'PENDING',
    total_amount numeric(19,4) NOT NULL CHECK (total_amount >= 0),
    currency     varchar(3)    NOT NULL DEFAULT 'USD',
    placed_at    timestamptz,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_id    ON retail.orders (user_id);
CREATE INDEX idx_orders_placed_at  ON retail.orders (placed_at);

CREATE TABLE retail.order_items (
    id           uuid          PRIMARY KEY,
    order_id     uuid          NOT NULL REFERENCES retail.orders (id) ON DELETE CASCADE,
    product_id   uuid          NOT NULL REFERENCES retail.products (id),
    product_sku  varchar(128),
    unit_price   numeric(19,4) NOT NULL CHECK (unit_price >= 0),
    quantity     int           NOT NULL CHECK (quantity > 0),
    line_total   numeric(19,4) NOT NULL CHECK (line_total >= 0),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_order_items_order_product ON retail.order_items (order_id, product_id);
CREATE INDEX        idx_order_items_order_id      ON retail.order_items (order_id);
CREATE INDEX        idx_order_items_product_id    ON retail.order_items (product_id);