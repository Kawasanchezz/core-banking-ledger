package com.corebanking.application;

/** Resultado de uma operação idempotente; {@code replayed} indica que a resposta veio do registro original. */
public record IdempotentResult<T>(T body, boolean replayed) {
}
