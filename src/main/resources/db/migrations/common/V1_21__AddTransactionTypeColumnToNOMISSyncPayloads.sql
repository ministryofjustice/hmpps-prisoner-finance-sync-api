ALTER TABLE nomis_sync_payloads
    ADD COLUMN transaction_type VARCHAR(24);

CREATE INDEX idx_nomis_sync_payloads_transaction_type
    ON nomis_sync_payloads (transaction_type);

-- backfill historical transaction type
UPDATE nomis_sync_payloads
SET transaction_type = body::jsonb
    -> 'offenderTransactions'
    -> 0
    ->> 'type'
WHERE jsonb_array_length(body::jsonb -> 'offenderTransactions') > 0;