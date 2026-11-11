package com.corebanking.infrastructure.repository;

import com.corebanking.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /** Extrato: lançamentos da conta no período [from, to), do mais recente para o mais antigo. */
    @Query(value = """
            select e from LedgerEntry e join fetch e.transaction
            where e.accountId = :accountId and e.createdAt >= :from and e.createdAt < :to
            order by e.createdAt desc, e.id desc
            """,
            countQuery = """
                    select count(e) from LedgerEntry e
                    where e.accountId = :accountId and e.createdAt >= :from and e.createdAt < :to
                    """)
    Page<LedgerEntry> findStatement(@Param("accountId") Long accountId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    Pageable pageable);
}
