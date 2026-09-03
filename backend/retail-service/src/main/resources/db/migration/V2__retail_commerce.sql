-- SentinelX retail-service · commerce (Phase 2): persistent per-user carts.
CREATE TABLE retail.cart_items (
    id         uuid  PRIMARY KEY,
    user_id    uuid  NOT NULL,
    product_id uuid  NOT NULL REFERENCES retail.products (id) ON DELETE CASCADE,
    quantity   int   NOT NULL CHECK (quantity > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_cart_items_user_product ON retail.cart_items (user_id, product_id);
CREATE INDEX        idx_cart_items_user_id     ON retail.cart_items (user_id);