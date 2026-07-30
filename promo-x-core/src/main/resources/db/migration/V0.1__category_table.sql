CREATE TABLE category (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR,
    name VARCHAR(128) NOT NULL,
    parent_id BIGINT,

    CONSTRAINT fk_parent_category FOREIGN KEY (parent_id) REFERENCES category (id)
);

CREATE UNIQUE INDEX idx_categories_external_id
ON category (external_id)
WHERE external_id IS NOT NULL;

