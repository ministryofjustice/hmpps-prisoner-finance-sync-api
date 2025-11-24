CREATE INDEX idx_transaction_entry_txn_id_acc_id_amount_type ON transaction_entry (transaction_id, account_id, amount, entry_type);
CREATE INDEX idx_transaction_entry_account_id ON transaction_entry (account_id);
CREATE INDEX idx_account_code_posting_type ON account (account_code, posting_type);
CREATE INDEX idx_transaction_prison_type_date ON transaction (prison, transaction_type, date);
CREATE INDEX idx_transaction_id_created_at ON transaction (id, created_at);