package io.digisic.bank.test.junit.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.digisic.bank.model.Account;
import io.digisic.bank.model.AccountTransaction;
import io.digisic.bank.model.AccountType;
import io.digisic.bank.model.TransactionCategory;
import io.digisic.bank.model.TransactionState;
import io.digisic.bank.model.TransactionType;
import io.digisic.bank.repository.AccountRepository;
import io.digisic.bank.repository.TransactionCategoryRepository;
import io.digisic.bank.repository.TransactionStateRepository;
import io.digisic.bank.repository.TransactionTypeRepository;
import io.digisic.bank.service.AccountService;
import io.digisic.bank.util.Constants;

/*
 * Wires AccountService against mocked repositories that hold one in-memory account.
 */
abstract class AccountServiceTestSupport {

	@Mock
	protected AccountRepository accountRepository;

	@Mock
	protected TransactionCategoryRepository transactionCategoryRepository;

	@Mock
	protected TransactionStateRepository transactionStateRepository;

	@Mock
	protected TransactionTypeRepository transactionTypeRepository;

	@InjectMocks
	protected AccountService accountService;

	protected Account account;

	@BeforeEach
	void wireMocks() {
		MockitoAnnotations.initMocks(this);
		when(accountRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(account));
		when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
		when(transactionCategoryRepository.findByCode(anyString()))
			.thenAnswer(inv -> new TransactionCategory(inv.getArgument(0), inv.getArgument(0)));
		when(transactionStateRepository.findByCode(anyString()))
			.thenAnswer(inv -> new TransactionState(inv.getArgument(0), inv.getArgument(0)));
		when(transactionTypeRepository.findByCode(anyString()))
			.thenAnswer(inv -> new TransactionType(inv.getArgument(0), inv.getArgument(0), TransactionType.CAT_EITHER));
	}

	protected Account newAccount(AccountType type, String balance) {
		Account acct = new Account();
		acct.setId(1L);
		acct.setName(type.getName());
		acct.setAccountType(type);
		acct.setCurrentBalance(new BigDecimal(balance));
		acct.setOpeningBalance(new BigDecimal(balance));
		acct.setAcountTransactionList(new ArrayList<AccountTransaction>());
		this.account = acct;
		return acct;
	}

	/* Account types exactly as seeded on master (flat 10.00 overdraft fee, 25.00 limit). */
	protected static AccountType legacyStandardChecking() {
		return new AccountType("SCK", Constants.ACCT_CHK_CAT, "Standard Checking", 0.00,
							   new BigDecimal(25.00), new BigDecimal(25.00), new BigDecimal(10.00));
	}

	protected static AccountType legacySavings() {
		return new AccountType("SAV", Constants.ACCT_SAV_CAT, "Savings", 1.85,
							   new BigDecimal(25.00), new BigDecimal(25.00), new BigDecimal(10.00));
	}

	protected static AccountType legacyMoneyMarket() {
		return new AccountType("MMA", Constants.ACCT_SAV_CAT, "Money Market", 2.02,
							   new BigDecimal(2500.00), new BigDecimal(25.00), new BigDecimal(10.00));
	}

	protected void debit(String amount) {
		AccountTransaction at = new AccountTransaction();
		at.setAmount(new BigDecimal(amount));
		at.setDescription("Test debit " + amount);
		at.setTransactionDate(new Date());
		at.setTransactionType(new TransactionType(Constants.ACCT_TRAN_TYPE_WITHDRAWL_CODE, "Withdrawl", TransactionType.CAT_DEBIT));
		accountService.debitTransaction(account, at);
	}

	protected List<AccountTransaction> transactionsOfType(String code) {
		return account.getAcountTransactionList().stream()
				.filter(t -> t.getTransactionType() != null && code.equals(t.getTransactionType().getCode()))
				.collect(Collectors.toList());
	}

	protected List<AccountTransaction> feeCategoryTransactions() {
		return account.getAcountTransactionList().stream()
				.filter(t -> t.getTransactionCategory() != null
						&& Constants.ACCT_TRAN_CAT_FEE_CODE.equals(t.getTransactionCategory().getCode()))
				.collect(Collectors.toList());
	}
}
