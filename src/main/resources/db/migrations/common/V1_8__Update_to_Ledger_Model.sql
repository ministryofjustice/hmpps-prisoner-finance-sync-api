ALTER TABLE account DROP COLUMN initial_debits;
ALTER TABLE account DROP COLUMN initial_credits;
ALTER TABLE account DROP COLUMN initial_on_hold;
ALTER TABLE account DROP COLUMN total_debits;
ALTER TABLE account DROP COLUMN total_credits;
ALTER TABLE account DROP COLUMN total_on_hold;

INSERT INTO transaction_type (TXN_TYPE, DESCRIPTION, ACTIVE_FLAG, TXN_USAGE, ALL_CASELOAD_FLAG, EXPIRY_DATE, UPDATE_ALLOWED_FLAG, MANUAL_INVOICE_FLAG, CREDIT_OBLIGATION_TYPE, LIST_SEQ, GROSS_NET_FLAG, CASELOAD_TYPE)
VALUES
    ('OB', 'Opening Balance', 'N', 'CR', 'Y', '', 'Y', 'Y', '', 0, 'N', 'INST'),
    ('OHB', 'Opening Hold Balance', 'N', 'CR', 'Y', '', 'Y', 'Y', '', 0, 'N', 'INST');

INSERT INTO account_code_lookup (account_code, name, classification, posting_type) VALUES
    (9999, 'Migration Clearing Account', 'Liability', 'CR');
