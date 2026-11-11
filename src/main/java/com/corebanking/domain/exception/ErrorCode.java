package com.corebanking.domain.exception;

/** Códigos de erro estáveis expostos na API (campo "code" do ProblemDetail). */
public enum ErrorCode {
    VALIDATION_ERROR(400, "Dados inválidos"),
    INVALID_AMOUNT(400, "Valor inválido"),
    INVALID_ACCOUNT_TYPE(400, "Tipo de conta inválido"),
    INVALID_PERIOD(400, "Período inválido"),
    IDEMPOTENCY_KEY_MISSING(400, "Idempotency-Key ausente ou inválido"),
    INVALID_CREDENTIALS(401, "Credenciais inválidas"),
    UNAUTHENTICATED(401, "Não autenticado"),
    ACCESS_DENIED(403, "Acesso negado"),
    ACCOUNT_NOT_FOUND(404, "Conta não encontrada"),
    DUPLICATE_CPF(409, "CPF já cadastrado"),
    DUPLICATE_EMAIL(409, "E-mail já cadastrado"),
    CONCURRENT_UPDATE(409, "Conflito de concorrência"),
    INSUFFICIENT_FUNDS(422, "Saldo insuficiente"),
    ACCOUNT_NOT_ACTIVE(422, "Conta não está ativa"),
    SAME_ACCOUNT_TRANSFER(422, "Transferência para a mesma conta"),
    IDEMPOTENCY_KEY_REUSED(422, "Idempotency-Key reutilizada com outro payload"),
    INTERNAL_ERROR(500, "Erro interno");

    private final int httpStatus;
    private final String title;

    ErrorCode(int httpStatus, String title) {
        this.httpStatus = httpStatus;
        this.title = title;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String title() {
        return title;
    }
}
