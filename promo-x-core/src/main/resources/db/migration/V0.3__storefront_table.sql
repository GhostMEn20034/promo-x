CREATE TABLE storefront (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(255) NOT NULL,
    website VARCHAR(255)
);

ALTER TABLE category ALTER COLUMN storefront_id TYPE BIGINT USING storefront_id::bigint;;