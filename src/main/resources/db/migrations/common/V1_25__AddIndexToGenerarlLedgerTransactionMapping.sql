
DROP INDEX IF EXISTS idx_gl_transaction_mapping_gl_transaction_uuid;

CREATE INDEX idx_gl_transaction_mapping_legacy_transaction_id
    on public.general_ledger_transaction_mapping (legacy_transaction_id);