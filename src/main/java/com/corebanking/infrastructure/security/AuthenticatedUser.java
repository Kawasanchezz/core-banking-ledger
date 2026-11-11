package com.corebanking.infrastructure.security;

/** Principal extraído do JWT. */
public record AuthenticatedUser(Long userId, Long customerId, String email) {
}
