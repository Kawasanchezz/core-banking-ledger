package com.corebanking.application;

import com.corebanking.domain.*;
import com.corebanking.infrastructure.repository.BankTransactionRepository;
import com.corebanking.infrastructure.repository.LedgerEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class LedgerPoster {

    private final BankTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final Clock clock;

    public LedgerPoster(BankTransactionRepository transactions, LedgerEntryRepository entries, Clock clock) {
        this.transactions = transactions;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BankTransaction post(TransactionType type, BigDecimal amount, String description,
                                Account debitAccount, Account creditAccount, Long initiatedBy, String idempotencyKey) {
        Instant now = clock.instant();

        debitAccount.debit(amount);
        creditAccount.credit(amount);

        BankTransaction tx = transactions.save(
                new BankTransaction(type, amount, description, initiatedBy, idempotencyKey, now));

        List<LedgerEntry> postings = List.of(
                new LedgerEntry(tx, debitAccount.getId(), EntryDirection.DEBIT, amount, debitAccount.getBalance(), now),
                new LedgerEntry(tx, creditAccount.getId(), EntryDirection.CREDIT, amount, creditAccount.getBalance(), now));

        assertBalanced(postings);
        postings.forEach(entries::save);
        return tx;
    }

    static void assertBalanced(List<LedgerEntry> postings) {
        BigDecimal debits = sum(postings, EntryDirection.DEBIT);
        BigDecimal credits = sum(postings, EntryDirection.CREDIT);
        if (debits.compareTo(credits) != 0) {
            throw new IllegalStateException("Ledger desbalanceado: débitos=" + debits + " créditos=" + credits);
        }
    }

    private static BigDecimal sum(List<LedgerEntry> postings, EntryDirection direction) {
        return postings.stream().filter(e -> e.getDirection() == direction)
                .map(LedgerEntry::getAmount).reduce(Money.ZERO, BigDecimal::add);
    }
}
