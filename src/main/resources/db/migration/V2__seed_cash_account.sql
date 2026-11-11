-- Conta contábil CAIXA do banco (contrapartida de depósitos e saques).
-- Convenção: saldo = créditos - débitos. A soma dos saldos de TODAS as contas é sempre 0.
INSERT INTO accounts (id, agency, number, type, status, balance, overdraft_limit, customer_id, version)
VALUES (1, '0000', '00000001', 'CAIXA', 'ATIVA', 0, 0, NULL, 0);

-- IDs de contas de clientes começam acima da conta CAIXA (id 1 = menor id = primeiro lock).
ALTER TABLE accounts ALTER COLUMN id RESTART WITH 1000;
