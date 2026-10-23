package io.digisic.bank.repository;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

import io.digisic.bank.model.PendingTransaction;


public interface PendingTransactionRepository extends CrudRepository<PendingTransaction, Long> {

	public List<PendingTransaction> findAllByStatus (String status);

}
