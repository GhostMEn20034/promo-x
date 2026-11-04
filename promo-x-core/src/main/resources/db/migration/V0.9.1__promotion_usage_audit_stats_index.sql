BEGIN TRANSACTION;

-- For the usage stats, which aggregate a storefront's audits over a created_at range.
CREATE INDEX idx_promotion_rule_usage_audit_storefront_id_created_at
    ON promotion_rule_usage_audit (storefront_id, created_at);

COMMIT TRANSACTION;
