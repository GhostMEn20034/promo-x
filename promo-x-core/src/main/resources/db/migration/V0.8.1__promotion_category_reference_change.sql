BEGIN TRANSACTION;

ALTER TABLE rule_category
    ALTER COLUMN category_id TYPE VARCHAR(255);

ALTER TABLE rule_category
    RENAME COLUMN category_id TO category_external_id;

COMMIT TRANSACTION;
