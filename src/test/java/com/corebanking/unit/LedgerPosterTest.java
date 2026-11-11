package com.corebanking.unit;

import com.corebanking.application.LedgerPoster;
import com.corebanking.domain.*;
import com.corebanking.infrastructure.repository.BankTransactionRepository;
import com.corebanking.infrastructure.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LedgerPosterTest {

    private final BankTransactionRepository txRepo = mock(BankTransactionRepository.class);
    private final LedgerEntryRepository entryRepo = mock(LedgerEntryRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private LedgerPoster poster;
    private Account from;
    private Account to;

    @BeforeEach
    void setUp() {
        poster = new LedgerPoster(txRepo, entryRepo, clock);
        when(txRepo.save(any(BankTransaction.class))).thenAnswer(i -> i.getArgument(0));
        from = Account.open("0001", "1", AccountType.CORRENTE, 1L, BigDecimal.ZERO, clock.instant());
        to = Account.open("0001", "2", AccountType.POUPANCA, 2L, BigDecimal.ZERO, clock.instant());
        ReflectionTestUtils.setField(from, "id", 10L);
        ReflectionTestUtils.setField(to, "id", 20L);
        from.credit(new BigDecimal("100.00"));
    }

    @Test
    void postsBalancedDebitAndCreditEntries() {
        BigDecimal amount = new BigDecimal("30.00");

        BankTransaction tx = poster.post(TransactionType.TRANSFER, amount, "teste", from, to, 1L, "key-1");

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepo, times(2)).save(captor.capture());
        List<LedgerEntry> entries = captor.getAllValues();

        assertThat(entries).extracting(LedgerEntry::getDirection)
                .containsExactly(EntryDirection.DEBIT, EntryDirection.CREDIT);
        assertThat(entries).extracting(LedgerEntry::getAccountId).containsExactly(10L, 20L);
        assertThat(entries).extracting(LedgerEntry::getAmount).allMatch(a -> a.compareTo(amount) == 0);
        assertThat(entries.get(0).getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(entries.get(1).getBalanceAfter()).isEqualByComparingTo("30.00");
        assertThat(tx.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(tx.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(from.getBalance()).isEqualByComparingTo("70.00");
        assertThat(to.getBalance()).isEqualByComparingTo("30.00");
    }

    @Test
    void insufficientFundsPostsNothing() {
        assertThatThrownBy(() -> poster.post(TransactionType.TRANSFER, new BigDecimal("100.01"), null, from, to, 1L, "k"))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(entryRepo);
        verify(txRepo, never()).save(any());
        assertThat(to.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void unbalancedPostingsAreRejected() {
        BankTransaction tx = new BankTransaction(TransactionType.TRANSFER, BigDecimal.TEN, null, 1L, "k", clock.instant());
        List<LedgerEntry> bad = List.of(
                new LedgerEntry(tx, 10L, EntryDirection.DEBIT, new BigDecimal("10.00"), BigDecimal.ZERO, clock.instant()),
                new LedgerEntry(tx, 20L, EntryDirection.CREDIT, new BigDecimal("9.99"), BigDecimal.ZERO, clock.instant()));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(LedgerPoster.class, "assertBalanced", bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("desbalanceado");
    }
}
