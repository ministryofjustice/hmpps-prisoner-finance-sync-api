ALTER TABLE account
    ADD COLUMN prison_id_unique_flag INT AS (CASE WHEN prison_id IS NULL THEN 1 ELSE NULL END);

CREATE UNIQUE INDEX uix_prisoner_trust_account
    ON account (prison_number, account_code, prison_id_unique_flag);