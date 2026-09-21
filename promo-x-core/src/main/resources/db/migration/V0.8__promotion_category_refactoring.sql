CREATE TABLE rule_category
(
    rule_id    BIGINT NOT NULL,
    category_id BIGINT NOT NULL,

    PRIMARY KEY (rule_id, category_id),
    CONSTRAINT rule_id_fk FOREIGN KEY (rule_id) REFERENCES promotion_rule (id) ON DELETE CASCADE
);

CREATE INDEX idx_rule_category_category_id ON rule_category (category_id);


ALTER TABLE category
    ALTER COLUMN external_id SET NOT NULL;