ALTER TABLE account_code_lookup
    ADD COLUMN parent_account_code INT NULL;

ALTER TABLE account_code_lookup
    ADD CONSTRAINT fk_account_code_parent
        FOREIGN KEY (parent_account_code)
            REFERENCES account_code_lookup (account_code);

UPDATE account_code_lookup SET parent_account_code = NULL WHERE account_code IN (1000, 2000, 3000, 4000, 9999, 10000);

UPDATE account_code_lookup SET parent_account_code = 1000 WHERE account_code IN (1100, 1500);
UPDATE account_code_lookup SET parent_account_code = 2000 WHERE account_code IN (2100, 2500, 20000, 21000, 25000);
UPDATE account_code_lookup SET parent_account_code = 3000 WHERE account_code IN (3101, 3102, 70000);
UPDATE account_code_lookup SET parent_account_code = 4000 WHERE account_code IN (4101, 4102, 4103, 4104, 80000);

UPDATE account_code_lookup SET parent_account_code = 10000 WHERE account_code IN (11000, 15000);

UPDATE account_code_lookup SET parent_account_code = 1100 WHERE account_code IN (1101, 1103, 1104, 1102);

UPDATE account_code_lookup SET parent_account_code = 1500 WHERE account_code IN (1501, 1502, 1503, 1504, 1505, 1506);

UPDATE account_code_lookup SET parent_account_code = 2100 WHERE account_code IN (2101, 2102, 2199, 2103);

UPDATE account_code_lookup SET parent_account_code = 2500 WHERE account_code IN (2501, 2502, 2503, 2504, 2505, 2506, 2507, 2508, 2509, 2510, 2511, 2512, 2513, 2514, 2515, 2516, 2518, 2519, 2520, 2521, 2522, 2523, 2524, 2525);

UPDATE account_code_lookup SET parent_account_code = 11000 WHERE account_code IN (11010, 11030, 11033, 11040, 11031, 11032, 11020, 11011);

UPDATE account_code_lookup SET parent_account_code = 15000 WHERE account_code IN (15010);

UPDATE account_code_lookup SET parent_account_code = 20000 WHERE account_code IN (21000, 25000);

UPDATE account_code_lookup SET parent_account_code = 21000 WHERE account_code IN (21010, 21020, 21030, 21040);

UPDATE account_code_lookup SET parent_account_code = 25000 WHERE account_code IN (
                                                                                  25010, 25020, 25030, 25040, 25050, 25060, 25070, 25080, 25090, 25100,
                                                                                  25110, 25120, 25130, 25135, 25140, 25150, 25160, 25170, 25171, 25172,
                                                                                  25173, 25174, 25180, 25190, 25200, 25210, 25211, 25212, 25213, 25230,
                                                                                  25240, 25260, 25270, 25280, 25290, 28000, 28005
    );

UPDATE account_code_lookup SET parent_account_code = 70000 WHERE account_code IN (71000);

UPDATE account_code_lookup SET parent_account_code = 71000 WHERE account_code IN (71010, 71020);

UPDATE account_code_lookup SET parent_account_code = 80000 WHERE account_code IN (81000);

UPDATE account_code_lookup SET parent_account_code = 81000 WHERE account_code IN (81010, 81020, 81030, 85000);
