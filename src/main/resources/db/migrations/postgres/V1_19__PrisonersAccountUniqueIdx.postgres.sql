CREATE UNIQUE INDEX uix_prisoner_trust_account
    ON account (prison_number, account_code) WHERE prison_id IS NULL;