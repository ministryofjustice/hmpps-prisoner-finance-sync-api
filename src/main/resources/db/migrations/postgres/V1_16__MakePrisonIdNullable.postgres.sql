ALTER TABLE account
    ALTER COLUMN prison_id DROP NOT NULL;

DROP INDEX IF EXISTS uix_general_ledger_account;

CREATE UNIQUE INDEX uix_general_ledger_account
    ON account (prison_id, account_code) WHERE prison_number IS NULL;