ALTER TABLE account
    ADD COLUMN gl_unique_flag INT AS (CASE WHEN prison_number IS NULL THEN 1 ELSE NULL END);
CREATE UNIQUE INDEX uix_general_ledger_account ON account (prison_id, account_code, gl_unique_flag);