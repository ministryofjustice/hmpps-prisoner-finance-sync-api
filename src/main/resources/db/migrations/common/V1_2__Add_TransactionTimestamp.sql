ALTER TABLE nomis_sync_payloads
    ADD COLUMN transaction_timestamp TIMESTAMP WITHOUT TIME ZONE;

CREATE INDEX idx_nomis_sync_payloads_transaction_timestamp ON nomis_sync_payloads (transaction_timestamp);