ALTER TABLE general_ledger_transaction_mapping
    ADD COLUMN transaction_type VARCHAR(24) default null,
    ADD COLUMN caseload_id VARCHAR(8) default null;


CREATE INDEX idx_gl_transaction_mapping_gl_transaction_uuid
    on public.general_ledger_transaction_mapping (gl_transaction_uuid);