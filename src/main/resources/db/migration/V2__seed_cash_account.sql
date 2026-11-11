INSERT INTO accounts (id, agency, number, type, status, balance, overdraft_limit, customer_id, version)
VALUES (1, '0000', '00000001', 'CAIXA', 'ATIVA', 0, 0, NULL, 0);

ALTER TABLE accounts ALTER COLUMN id RESTART WITH 1000;
