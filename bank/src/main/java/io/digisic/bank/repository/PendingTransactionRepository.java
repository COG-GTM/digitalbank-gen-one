package io.digisic.bank.repository;

import java.util.List;
import java.util.Optional;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.digisic.bank.model.Account;
import org.springframework.data.repository.CrudRepository;

import io.digisic.bank.model.PendingTransaction;


public interface PendingTransactionRepository extends CrudRepository<PendingTransaction, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PendingTransaction p where p.id = :id")
	public Optional<PendingTransaction> findByIdForUpdate (@Param("id") Long id);

	public void deleteByAccountOrToAccount (Account account, Account toAccount);

	public List<PendingTransaction> findAllByStatus (String status);

}
