DROP INDEX IF EXISTS idx_nomis_sync_payloads_pagination;

CREATE INDEX idx_nomis_sync_payloads_pagination
    ON nomis_sync_payloads (
                            timestamp,
                            caseload_id,
                            transaction_type,
                            legacy_transaction_id,
                            id
        );