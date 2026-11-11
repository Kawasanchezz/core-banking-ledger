package com.corebanking.infrastructure.repository;

import com.corebanking.domain.Account;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** SELECT ... FOR UPDATE. Timeout de 5s para não esperar indefinidamente por um lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    List<Account> findByCustomerIdOrderById(Long customerId);

    /** Checagem de dono SEM carregar a entidade no persistence context (evita estado obsoleto antes do FOR UPDATE). */
    boolean existsByIdAndCustomerId(Long id, Long customerId);
}
