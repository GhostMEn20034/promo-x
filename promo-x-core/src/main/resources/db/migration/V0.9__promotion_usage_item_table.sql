BEGIN TRANSACTION;

-- One row per evaluation in which at least one rule was applied.
CREATE TABLE promotion_rule_usage
(
    id               UUID                     NOT NULL,
    storefront_id    BIGINT                   NOT NULL,
    status           VARCHAR(32)              NOT NULL,
    -- Every applied rule, in application order. An ID array, so reading a usage needs no join.
    applied_rule_ids BIGINT[]                 NOT NULL,
    -- Subset of applied_rule_ids whose usage limit this usage holds; given back on cancel.
    limited_rule_ids BIGINT[]                 NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    -- When a RESERVED usage is due to expire if not confirmed; NULL when the usage was never reserved.
    expires_at       TIMESTAMP WITH TIME ZONE,
    -- When the usage was actually moved to EXPIRED; NULL until then.
    expired_at       TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_promotion_rule_usage PRIMARY KEY (id),
    CONSTRAINT chk_promotion_rule_usage_status
        CHECK (status IN ('RESERVED', 'USED', 'EXPIRED', 'CANCELED'))
);

-- For the expiration job, which looks up overdue reservations every minute. Partial, so it only
-- holds the usages still waiting to be confirmed.
CREATE INDEX idx_promotion_rule_usage_reserved_expires_at
    ON promotion_rule_usage (expires_at)
    WHERE status = 'RESERVED';

-- Shares its primary key with the usage it describes (one audit per usage).
CREATE TABLE promotion_rule_usage_audit
(
    usage_id             UUID                     NOT NULL,
    storefront_id        BIGINT                   NOT NULL,
    session_id           VARCHAR(255),
    customer_id          VARCHAR(255),
    promo_codes          VARCHAR(255)[]           NOT NULL,
    shipping_cost_before NUMERIC(19, 4)           NOT NULL,
    shipping_cost_after  NUMERIC(19, 4)           NOT NULL,
    subtotal_before      NUMERIC(19, 4)           NOT NULL,
    subtotal_after       NUMERIC(19, 4)           NOT NULL,
    total_before         NUMERIC(19, 4)           NOT NULL,
    total_after          NUMERIC(19, 4)           NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_promotion_rule_usage_audit PRIMARY KEY (usage_id),
    CONSTRAINT fk_promotion_rule_usage_audit_usage
        FOREIGN KEY (usage_id) REFERENCES promotion_rule_usage (id) ON DELETE CASCADE
);

CREATE TABLE promotion_rule_usage_audit_applied_rule
(
    usage_id UUID   NOT NULL,
    rule_id  BIGINT NOT NULL,

    CONSTRAINT pk_promotion_rule_usage_audit_applied_rule PRIMARY KEY (usage_id, rule_id),
    CONSTRAINT fk_promotion_rule_usage_audit_applied_rule_audit
        FOREIGN KEY (usage_id) REFERENCES promotion_rule_usage_audit (usage_id) ON DELETE CASCADE,
    CONSTRAINT fk_promotion_rule_usage_audit_applied_rule_rule
        FOREIGN KEY (rule_id) REFERENCES promotion_rule (id) ON DELETE CASCADE
);

CREATE INDEX idx_promotion_rule_usage_audit_applied_rule_rule_id
    ON promotion_rule_usage_audit_applied_rule (rule_id);

-- Rules whose conditions matched, whether or not their action was applied.
CREATE TABLE promotion_rule_usage_audit_matched_rule
(
    usage_id UUID   NOT NULL,
    rule_id  BIGINT NOT NULL,

    CONSTRAINT pk_promotion_rule_usage_audit_matched_rule PRIMARY KEY (usage_id, rule_id),
    CONSTRAINT fk_promotion_rule_usage_audit_matched_rule_audit
        FOREIGN KEY (usage_id) REFERENCES promotion_rule_usage_audit (usage_id) ON DELETE CASCADE,
    CONSTRAINT fk_promotion_rule_usage_audit_matched_rule_rule
        FOREIGN KEY (rule_id) REFERENCES promotion_rule (id) ON DELETE CASCADE
);

CREATE INDEX idx_promotion_rule_usage_audit_matched_rule_rule_id
    ON promotion_rule_usage_audit_matched_rule (rule_id);

-- One row per order line of the audited evaluation.
CREATE TABLE promotion_rule_usage_audit_product
(
    id                  UUID           NOT NULL,
    rule_usage_audit_id UUID           NOT NULL,
    storefront_id       BIGINT         NOT NULL,
    product_external_id VARCHAR(255)   NOT NULL,
    product_sku         VARCHAR(255)   NOT NULL,
    -- External IDs of the product's categories at evaluation time.
    categories          VARCHAR(255)[] NOT NULL,
    quantity            INTEGER        NOT NULL,
    unit_price          NUMERIC(19, 4) NOT NULL,

    CONSTRAINT pk_promotion_rule_usage_audit_product PRIMARY KEY (id),
    CONSTRAINT fk_promotion_rule_usage_audit_product_audit
        FOREIGN KEY (rule_usage_audit_id) REFERENCES promotion_rule_usage_audit (usage_id) ON DELETE CASCADE
);

CREATE INDEX idx_promotion_rule_usage_audit_product_audit_id
    ON promotion_rule_usage_audit_product (rule_usage_audit_id);

COMMIT TRANSACTION;
