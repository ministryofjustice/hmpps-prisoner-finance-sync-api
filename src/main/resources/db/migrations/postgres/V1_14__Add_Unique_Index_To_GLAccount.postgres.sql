CREATE UNIQUE INDEX uix_general_ledger_account
    ON account (prison_id, account_code) WHERE prison_number IS NULL;