CREATE TABLE product (
    id            BIGSERIAL PRIMARY KEY,
    external_id   VARCHAR(255),
    sku           VARCHAR(255)                            NOT NULL,
    name          VARCHAR(255)                            NOT NULL,
    storefront_id BIGINT                                  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE                NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE                NOT NULL,

    CONSTRAINT uniq_product_storefront_external_id UNIQUE (storefront_id, external_id)
);

CREATE TABLE product_category
(
    product_id  BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_product_category_on_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
);

CREATE INDEX idx_product_category_category_id ON product_category (category_id);