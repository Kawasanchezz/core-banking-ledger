package com.corebanking.domain;

public enum AccountType {
    CORRENTE,
    POUPANCA,
    /** Conta contábil interna do banco (contrapartida de depósitos e saques). Não é aberta por clientes. */
    CAIXA
}
