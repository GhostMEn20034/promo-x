CREATE TABLE promotion_rule (
    id               BIGSERIAL PRIMARY KEY,
    storefront_id    BIGINT                                  NOT NULL,
    name             VARCHAR(255)                            NOT NULL,
    priority         INTEGER                                 NOT NULL,
    is_short_circuit BOOLEAN                                 NOT NULL,
    is_active        BOOLEAN                                 NOT NULL,
    condition_tree   JSONB                                   NOT NULL,
    action           JSONB                                   NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE                NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE                NOT NULL
);

CREATE TABLE rule_product
(
    rule_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    PRIMARY KEY (rule_id, product_id),
    CONSTRAINT rule_id_fk FOREIGN KEY (rule_id) REFERENCES promotion_rule (id) ON DELETE CASCADE
);

CREATE INDEX idx_rule_product_product_id ON rule_product (product_id);