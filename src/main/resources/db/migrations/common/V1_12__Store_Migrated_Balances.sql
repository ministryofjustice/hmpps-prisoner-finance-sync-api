CREATE TABLE migrated_general_ledger_balances (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  prison_id VARCHAR(3) NOT NULL,
                                                  timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
                                                  body JSON NOT NULL
);

CREATE INDEX idx_mglb_prison_id ON migrated_general_ledger_balances (prison_id);


CREATE TABLE migrated_prisoner_balances (
                                            id BIGSERIAL PRIMARY KEY,
                                            prison_number VARCHAR(10) NOT NULL,
                                            timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
                                            body JSON NOT NULL
);

CREATE INDEX idx_mpb_prison_number ON migrated_prisoner_balances (prison_number);