ALTER TABLE nomis_sync_payloads
    ALTER COLUMN request_id SET NOT NULL;

ALTER TABLE nomis_sync_payloads
    ADD CONSTRAINT UQ_nomis_sync_payloads_request_id UNIQUE (request_id);

