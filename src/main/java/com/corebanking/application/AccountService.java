package com.corebanking.application;

import com.corebanking.api.dto.AccountDtos.AccountResponse;
import com.corebanking.api.dto.AccountDtos.BalanceResponse;
import com.corebanking.domain.Account;
import com.corebanking.domain.AccountType;
import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import com.corebanking.infrastructure.config.BankProperties;
import com.corebanking.infrastructure.repository.AccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;
    private final BankProperties props;
    private final Clock clock;

    public AccountService(AccountRepository accounts, JdbcTemplate jdbc, BankProperties props, Clock clock) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public AccountResponse open(Long customerId, AccountType type) {
        if (type == AccountType.CAIXA) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_TYPE, "Tipo de conta inválido");
        }
        Long seq = jdbc.queryForObject("SELECT nextval('account_number_seq')", Long.class);
        String number = String.format("%08d", seq);
        Account account = Account.open(props.account().agency(), number, type, customerId,
                props.account().checkingOverdraftLimit(), clock.instant());
        return AccountResponse.from(accounts.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long customerId, Long accountId) {
        return AccountResponse.from(requireOwned(customerId, accountId));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long customerId) {
        return accounts.findByCustomerIdOrderById(customerId).stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(Long customerId, Long accountId) {
        Account a = requireOwned(customerId, accountId);
        return new BalanceResponse(a.getId(), a.getBalance(), a.availableBalance(), clock.instant());
    }

    /**
     * 404 se não existe, 403 se pertence a outro cliente. NÃO carrega a conta no persistence context:
     * usado antes de bloquear (FOR UPDATE), pois uma instância gerenciada obsoleta seria reutilizada pelo Hibernate.
     */
    @Transactional(readOnly = true)
    public void assertOwned(Long customerId, Long accountId) {
        if (accounts.existsByIdAndCustomerId(accountId, customerId)) {
            return;
        }
        if (accounts.existsById(accountId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Você não tem acesso a esta conta");
        }
        throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada");
    }

    /** Leitura sem lock: 404 se não existe, 403 se pertence a outro cliente. */
    @Transactional(readOnly = true)
    public Account requireOwned(Long customerId, Long accountId) {
        Account a = accounts.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada"));
        if (!a.isOwnedBy(customerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Você não tem acesso a esta conta");
        }
        return a;
    }
}
