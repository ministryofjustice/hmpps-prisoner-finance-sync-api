DROP INDEX IF EXISTS idx_nomis_sync_payloads_transaction_id;

ALTER TABLE nomis_sync_payloads
ALTER COLUMN transaction_id TYPE BIGINT USING transaction_id::BIGINT;

CREATE INDEX idx_nomis_sync_payloads_transaction_id ON nomis_sync_payloads (transaction_id);

