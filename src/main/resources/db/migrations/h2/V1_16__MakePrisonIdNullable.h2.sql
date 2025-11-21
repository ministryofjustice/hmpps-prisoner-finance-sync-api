DROP INDEX IF EXISTS uix_general_ledger_account;

ALTER TABLE account
    ADD COLUMN IF NOT EXISTS gl_unique_flag INT AS (CASE WHEN prison_number IS NULL THEN 1 ELSE NULL END);

ALTER TABLE account
    ALTER COLUMN prison_id DROP NOT NULL;

CREATE UNIQUE INDEX uix_general_ledger_account_partial
    ON account (prison_id, account_code, gl_unique_flag);