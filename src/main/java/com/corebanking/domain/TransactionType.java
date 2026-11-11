package com.corebanking.domain;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    /** Estorno: sempre um novo lançamento compensatório, nunca uma alteração do original. */
    REVERSAL
}
