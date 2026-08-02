CREATE TABLE category (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR,
    name VARCHAR(128) NOT NULL,
    parent_id BIGINT,
    storefront_id VARCHAR NOT NULL,

    CONSTRAINT fk_parent_category FOREIGN KEY (parent_id) REFERENCES category (id)
);

CREATE UNIQUE INDEX idx_category_storefront_external_id
ON category (storefront_id, external_id)
WHERE external_id IS NOT NULL;

