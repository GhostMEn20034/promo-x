BEGIN TRANSACTION;

ALTER TABLE product
    ALTER COLUMN external_id SET NOT NULL;

ALTER TABLE rule_product
    ALTER COLUMN product_id TYPE VARCHAR(255);

ALTER TABLE rule_product
    RENAME COLUMN product_id TO product_external_id;

COMMIT TRANSACTION;
