ALTER TABLE transaction
    ADD COLUMN modified_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE transaction
    ADD COLUMN audit_status VARCHAR(20);
