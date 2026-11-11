package com.corebanking.application;

public record IdempotentResult<T>(T body, boolean replayed) {
}
