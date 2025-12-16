INSERT INTO transaction_type (TXN_TYPE, DESCRIPTION, ACTIVE_FLAG, TXN_USAGE, ALL_CASELOAD_FLAG, EXPIRY_DATE, UPDATE_ALLOWED_FLAG, MANUAL_INVOICE_FLAG, CREDIT_OBLIGATION_TYPE, LIST_SEQ, GROSS_NET_FLAG, CASELOAD_TYPE)
VALUES
    ('TOB', 'Transfer Opening Balance', 'N', 'CR', 'Y', '', 'Y', 'Y', '', 0, 'N', 'INST'),
    ('ROB', 'Reverse Opening Balance', 'N', 'CR', 'Y', '', 'Y', 'Y', '', 0, 'N', 'INST'),
    ('TOHB', 'Transfer Opening Hold Balance', 'N', 'CR', 'Y', '', 'Y', 'Y', '', 0, 'N', 'INST'),
    ('ROHB', 'Reverse Opening Hold Balance', 'N', 'CR', 'Y', '', 'Y', 'Y', '', 0, 'N', 'INST');


