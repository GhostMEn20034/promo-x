ALTER TABLE promotion_rule
    ADD COLUMN has_usage_limit BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN usage_limit INTEGER DEFAULT 0 NOT NULL,
    ADD COLUMN remaining_uses INTEGER DEFAULT 0 NOT NULL,
    ADD CONSTRAINT chk_promotion_rule_usage_limit CHECK (usage_limit >= 0),
    ADD CONSTRAINT chk_promotion_rule_remaining_uses CHECK (remaining_uses >= 0);
