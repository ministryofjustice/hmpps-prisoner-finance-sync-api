ALTER TABLE migrated_general_ledger_balances
ALTER COLUMN prison_id TYPE VARCHAR(24);

ALTER TABLE prison
ALTER COLUMN code TYPE VARCHAR(24);