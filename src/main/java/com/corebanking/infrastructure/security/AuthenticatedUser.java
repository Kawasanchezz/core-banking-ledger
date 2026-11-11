package com.corebanking.infrastructure.security;

public record AuthenticatedUser(Long userId, Long customerId, String email) {
}
