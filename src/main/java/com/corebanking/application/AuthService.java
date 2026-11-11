package com.corebanking.application;

import com.corebanking.api.dto.AuthDtos.*;
import com.corebanking.domain.AppUser;
import com.corebanking.domain.Cpf;
import com.corebanking.domain.Customer;
import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import com.corebanking.infrastructure.repository.CustomerRepository;
import com.corebanking.infrastructure.repository.UserRepository;
import com.corebanking.infrastructure.security.AuthenticatedUser;
import com.corebanking.infrastructure.security.JwtService;
import com.corebanking.infrastructure.security.LoginAttemptLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final CustomerRepository customers;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttemptLimiter attempts;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(CustomerRepository customers, UserRepository users, PasswordEncoder encoder,
                       JwtService jwt, LoginAttemptLimiter attempts, Clock clock) {
        this.customers = customers;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.attempts = attempts;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public CustomerResponse register(RegisterRequest req) {
        String cpf = Cpf.normalize(req.cpf());
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (customers.existsByCpf(cpf)) {
            throw new BusinessException(ErrorCode.DUPLICATE_CPF, "CPF já cadastrado");
        }
        if (users.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "E-mail já cadastrado");
        }
        Customer customer = customers.save(new Customer(req.name().trim(), cpf, clock.instant()));
        users.save(new AppUser(email, encoder.encode(req.password()), customer.getId(), clock.instant()));
        log.info("Cliente cadastrado id={} cpf={}", customer.getId(), Cpf.mask(cpf));
        return new CustomerResponse(customer.getId(), customer.getName(), email, Cpf.mask(cpf));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req, String clientIp) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        attempts.checkAllowed(email, clientIp);
        AppUser user = users.findByEmail(email).orElse(null);
        boolean ok = encoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok) {
            attempts.recordFailure(email, clientIp);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "E-mail ou senha inválidos");
        }
        attempts.recordSuccess(email);
        String token = jwt.generate(new AuthenticatedUser(user.getId(), user.getCustomerId(), user.getEmail()));
        return new TokenResponse(token, "Bearer", jwt.expiresInSeconds());
    }
}
