package com.corebanking.application;

import com.corebanking.api.dto.TransactionDtos.PageResponse;
import com.corebanking.api.dto.TransactionDtos.StatementEntryResponse;
import com.corebanking.domain.LedgerEntry;
import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import com.corebanking.infrastructure.repository.LedgerEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class StatementService {

    static final int MAX_PAGE_SIZE = 100;
    private static final LocalDate MIN_DATE = LocalDate.of(1970, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9998, 12, 31);
    private static final Instant MIN = Instant.EPOCH;
    private static final Instant MAX = Instant.parse("9999-01-01T00:00:00Z");

    private final AccountService accountService;
    private final LedgerEntryRepository entries;

    public StatementService(AccountService accountService, LedgerEntryRepository entries) {
        this.accountService = accountService;
        this.entries = entries;
    }

    /**
     * Extrato paginado. {@code from} e {@code to} são datas (UTC), ambas inclusivas e opcionais.
     */
    @Transactional(readOnly = true)
    public PageResponse<StatementEntryResponse> statement(Long customerId, Long accountId,
                                                          LocalDate from, LocalDate to, int page, int size) {
        accountService.requireOwned(customerId, accountId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "page deve ser >= 0 e size entre 1 e " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD, "'from' deve ser anterior ou igual a 'to'");
        }
        if (outOfRange(from) || outOfRange(to)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD,
                    "As datas devem estar entre " + MIN_DATE + " e " + MAX_DATE);
        }
        Instant start = from == null ? MIN : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endExclusive = to == null ? MAX : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Page<LedgerEntry> result = entries.findStatement(accountId, start, endExclusive, PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent().stream().map(StatementService::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Datas absurdas (ex.: ano 999999999) estourariam a conversão para Instant/Timestamp e virariam erro 500. */
    private static boolean outOfRange(LocalDate date) {
        return date != null && (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE));
    }

    private static StatementEntryResponse toResponse(LedgerEntry e) {
        return new StatementEntryResponse(e.getId(), e.getTransaction().getId(), e.getTransaction().getType(),
                e.getDirection(), e.getAmount(), e.getBalanceAfter(), e.getTransaction().getDescription(),
                e.getCreatedAt());
    }
}
