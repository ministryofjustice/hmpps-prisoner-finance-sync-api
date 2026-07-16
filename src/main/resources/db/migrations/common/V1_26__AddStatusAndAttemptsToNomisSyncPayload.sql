ALTER TABLE nomis_sync_payloads
    ADD COLUMN status VARCHAR(24);

CREATE INDEX idx_nomis_sync_payloads_status
    ON nomis_sync_payloads (status);

ALTER TABLE nomis_sync_payloads
    ADD COLUMN attempts INT;

CREATE INDEX idx_nomis_sync_payloads_status
    ON nomis_sync_payloads (attempts);

