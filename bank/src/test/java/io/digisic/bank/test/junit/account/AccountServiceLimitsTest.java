package io.digisic.bank.test.junit.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.digisic.bank.exception.RestForbiddenException;
import io.digisic.bank.exception.RestNotAcceptableException;
import io.digisic.bank.exception.TransactionLimitException;
import io.digisic.bank.model.Account;
import io.digisic.bank.model.AccountTransaction;
import io.digisic.bank.model.AccountType;
import io.digisic.bank.model.PendingTransaction;
import io.digisic.bank.model.TransactionCategory;
import io.digisic.bank.model.TransactionState;
import io.digisic.bank.model.TransactionType;
import io.digisic.bank.model.security.Users;
import io.digisic.bank.repository.AccountRepository;
import io.digisic.bank.repository.AccountTransactionRepository;
import io.digisic.bank.repository.PendingTransactionRepository;
import io.digisic.bank.repository.TransactionCategoryRepository;
import io.digisic.bank.repository.TransactionStateRepository;
import io.digisic.bank.repository.TransactionTypeRepository;
import io.digisic.bank.service.AccountService;
import io.digisic.bank.util.Constants;

/**
 * EM-134: service-layer balance/overdraft, per-transaction and daily limits, and maker-checker.
 */
public class AccountServiceLimitsTest {

	private AccountService accountService;
	private AccountRepository accountRepository;
	private AccountTransactionRepository accountTransactionRepository;
	private PendingTransactionRepository pendingTransactionRepository;
	private Account account;
	private Account toAccount;
	private Users maker;
	private Users checker;

	@BeforeEach
	public void setUp() {

		accountRepository = mock(AccountRepository.class);
		accountTransactionRepository = mock(AccountTransactionRepository.class);
		pendingTransactionRepository = mock(PendingTransactionRepository.class);
		TransactionTypeRepository transactionTypeRepository = mock(TransactionTypeRepository.class);
		TransactionStateRepository transactionStateRepository = mock(TransactionStateRepository.class);
		TransactionCategoryRepository transactionCategoryRepository = mock(TransactionCategoryRepository.class);

		when(transactionTypeRepository.findByCode(anyString())).thenReturn(new TransactionType("TRN", "Transfer", TransactionType.CAT_EITHER));
		when(transactionStateRepository.findByCode(anyString())).thenReturn(new TransactionState("COM", "Complete"));
		when(transactionCategoryRepository.findByCode(anyString())).thenReturn(new TransactionCategory("MISC", "Misc"));
		when(pendingTransactionRepository.save(any(PendingTransaction.class))).thenAnswer(i -> i.getArgument(0));

		accountService = new AccountService();
		ReflectionTestUtils.setField(accountService, "accountRepository", accountRepository);
		ReflectionTestUtils.setField(accountService, "accountTransactionRepository", accountTransactionRepository);
		ReflectionTestUtils.setField(accountService, "pendingTransactionRepository", pendingTransactionRepository);
		ReflectionTestUtils.setField(accountService, "transactionTypeRepository", transactionTypeRepository);
		ReflectionTestUtils.setField(accountService, "transactionStateRepository", transactionStateRepository);
		ReflectionTestUtils.setField(accountService, "transactionCategoryRepository", transactionCategoryRepository);
		ReflectionTestUtils.setField(accountService, "perTransactionLimit", new BigDecimal("1000.00"));
		ReflectionTestUtils.setField(accountService, "dailyLimit", new BigDecimal("1500.00"));
		ReflectionTestUtils.setField(accountService, "approvalThreshold", new BigDecimal("500.00"));

		AccountType checking = new AccountType("SCK", "CHK", "Standard Checking", 0.00,
				new BigDecimal("25.00"), new BigDecimal("100.00"), new BigDecimal("10.00"));

		account = account(1L, "2000.00", checking);
		toAccount = account(2L, "0.00", checking);
		when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
		when(accountRepository.findById(2L)).thenReturn(Optional.of(toAccount));

		maker = user(100L);
		checker = user(200L);
	}

	// --- Balance + arranged overdraft ------------------------------------------------------------

	@Test
	public void debitAboveBalancePlusOverdraftIsRejectedAndNothingPosted() {
		account.setCurrentBalance(new BigDecimal("50.00"));

		Assertions.assertThrows(TransactionLimitException.class, () -> accountService.debitTransaction(account, txn("150.01")));

		verify(accountRepository, never()).save(any());
		Assertions.assertEquals(0, new BigDecimal("50.00").compareTo(account.getCurrentBalance()));
	}

	@Test
	public void debitExactlyAtBalancePlusOverdraftIsPosted() {
		account.setCurrentBalance(new BigDecimal("50.00"));

		accountService.debitTransaction(account, txn("150.00"));

		Assertions.assertTrue(account.getCurrentBalance().compareTo(new BigDecimal("-100.00")) <= 0);
		verify(accountRepository, times(2)).save(account); // debit + overdraft fee
	}

	@Test
	public void transferAboveBalancePlusOverdraftPostsNothingOnEitherAccount() {
		account.setCurrentBalance(new BigDecimal("10.00"));

		Assertions.assertThrows(TransactionLimitException.class, () -> accountService.transfer(account, toAccount, txn("110.01")));

		verify(accountRepository, never()).save(any());
		Assertions.assertTrue(account.getAcountTransactionList().isEmpty());
		Assertions.assertTrue(toAccount.getAcountTransactionList().isEmpty());
	}

	@Test
	public void nonPositiveDebitIsRejected() {
		Assertions.assertThrows(TransactionLimitException.class, () -> accountService.debitTransaction(account, txn("0.00")));
		verify(accountRepository, never()).save(any());
	}

	// --- Per-transaction limit -------------------------------------------------------------------

	@Test
	public void debitOverPerTransactionLimitIsRejected() {
		Assertions.assertThrows(TransactionLimitException.class, () -> accountService.debitTransaction(account, txn("1000.01")));
		verify(accountRepository, never()).save(any());
	}

	@Test
	public void debitAtPerTransactionLimitIsPosted() {
		accountService.debitTransaction(account, txn("1000.00"));
		Assertions.assertEquals(0, new BigDecimal("1000.00").compareTo(account.getCurrentBalance()));
	}

	@Test
	public void transferOverPerTransactionLimitIsRejected() {
		Assertions.assertThrows(TransactionLimitException.class, () -> accountService.transfer(account, toAccount, txn("1000.01")));
		verify(accountRepository, never()).save(any());
	}

	// --- Daily limit -----------------------------------------------------------------------------

	@Test
	public void debitThatWouldExceedDailyLimitIsRejected() {
		givenDebitsToday("-600.00", "-400.00");

		Assertions.assertThrows(TransactionLimitException.class, () -> accountService.debitTransaction(account, txn("500.01")));
		verify(accountRepository, never()).save(any());
	}

	@Test
	public void debitThatReachesDailyLimitExactlyIsPosted() {
		givenDebitsToday("-600.00", "-400.00");

		accountService.debitTransaction(account, txn("500.00"));
		Assertions.assertEquals(0, new BigDecimal("1500.00").compareTo(account.getCurrentBalance()));
	}

	@Test
	public void overdraftFeesDoNotCountTowardsDailyLimit() {
		AccountTransaction fee = new AccountTransaction();
		fee.setAmount(new BigDecimal("-900.00"));
		fee.setTransactionCategory(new TransactionCategory(Constants.ACCT_TRAN_CAT_FEE_CODE, "Fee"));
		AccountTransaction payment = new AccountTransaction();
		payment.setAmount(new BigDecimal("-1000.00"));
		when(accountTransactionRepository.findAllByAccountAndAmountLessThanAndTransactionDateBetween(eq(account), any(), any(), any()))
			.thenReturn(Arrays.asList(fee, payment));

		accountService.debitTransaction(account, txn("500.00"));
	}

	@Test
	public void limitsAreConfigurableAndZeroDisables() {
		ReflectionTestUtils.setField(accountService, "perTransactionLimit", BigDecimal.ZERO);
		ReflectionTestUtils.setField(accountService, "dailyLimit", BigDecimal.ZERO);

		accountService.debitTransaction(account, txn("2000.00"));
		Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(account.getCurrentBalance()));
	}

	@Test
	public void approvalThresholdBoundary() {
		Assertions.assertFalse(accountService.requiresApproval(new BigDecimal("500.00")));
		Assertions.assertTrue(accountService.requiresApproval(new BigDecimal("500.01")));
	}

	// --- Maker-checker ---------------------------------------------------------------------------

	@Test
	public void submitHoldsTransactionPendingWithoutPosting() {
		PendingTransaction pending = accountService.submitForApproval(account, null, txn("250.00"), false, maker);

		Assertions.assertEquals(PendingTransaction.STATUS_PENDING, pending.getStatus());
		Assertions.assertSame(maker, pending.getMaker());
		Assertions.assertNull(pending.getChecker());
		verify(accountRepository, never()).save(any());
		Assertions.assertEquals(0, new BigDecimal("2000.00").compareTo(account.getCurrentBalance()));
	}

	@Test
	public void submitDebitOverLimitIsRejectedImmediately() {
		account.setCurrentBalance(new BigDecimal("10.00"));
		Assertions.assertThrows(TransactionLimitException.class,
				() -> accountService.submitForApproval(account, null, txn("110.01"), true, maker));
		verify(pendingTransactionRepository, never()).save(any());
	}

	@Test
	public void makerCannotApproveOwnTransaction() {
		PendingTransaction pending = givenPending(false, null, "250.00");

		Assertions.assertThrows(RestForbiddenException.class, () -> accountService.approvePendingTransaction(1L, user(100L)));

		Assertions.assertEquals(PendingTransaction.STATUS_PENDING, pending.getStatus());
		verify(accountRepository, never()).save(any());
	}

	@Test
	public void makerCannotRejectOwnTransaction() {
		PendingTransaction pending = givenPending(false, null, "250.00");

		Assertions.assertThrows(RestForbiddenException.class, () -> accountService.rejectPendingTransaction(1L, user(100L)));
		Assertions.assertEquals(PendingTransaction.STATUS_PENDING, pending.getStatus());
	}

	@Test
	public void differentCheckerApprovesAndCreditIsPosted() {
		givenPending(false, null, "250.00");

		PendingTransaction result = accountService.approvePendingTransaction(1L, checker);

		Assertions.assertEquals(PendingTransaction.STATUS_APPROVED, result.getStatus());
		Assertions.assertSame(checker, result.getChecker());
		Assertions.assertNotNull(result.getDecidedDate());
		Assertions.assertEquals(0, new BigDecimal("2250.00").compareTo(account.getCurrentBalance()));
	}

	@Test
	public void differentCheckerApprovesTransferAndBothSidesPost() {
		givenPending(true, toAccount, "750.00");

		accountService.approvePendingTransaction(1L, checker);

		Assertions.assertEquals(0, new BigDecimal("1250.00").compareTo(account.getCurrentBalance()));
		Assertions.assertEquals(0, new BigDecimal("750.00").compareTo(toAccount.getCurrentBalance()));
	}

	@Test
	public void approvalReChecksLimitsAndKeepsPendingOnBreach() {
		PendingTransaction pending = givenPending(true, null, "250.00");
		account.setCurrentBalance(new BigDecimal("10.00"));

		Assertions.assertThrows(TransactionLimitException.class, () -> accountService.approvePendingTransaction(1L, checker));
		Assertions.assertEquals(PendingTransaction.STATUS_PENDING, pending.getStatus());
		verify(accountRepository, never()).save(any());
	}

	@Test
	public void differentCheckerRejectsAndNothingIsPosted() {
		givenPending(true, null, "250.00");

		PendingTransaction result = accountService.rejectPendingTransaction(1L, checker);

		Assertions.assertEquals(PendingTransaction.STATUS_REJECTED, result.getStatus());
		Assertions.assertSame(checker, result.getChecker());
		verify(accountRepository, never()).save(any());
	}

	@Test
	public void decidedTransactionCannotBeDecidedAgain() {
		PendingTransaction pending = givenPending(false, null, "250.00");
		pending.setStatus(PendingTransaction.STATUS_APPROVED);

		Assertions.assertThrows(RestNotAcceptableException.class, () -> accountService.approvePendingTransaction(1L, checker));
		verify(accountRepository, never()).save(any());
	}

	// --- helpers ---------------------------------------------------------------------------------

	private void givenDebitsToday(String... amounts) {
		java.util.List<AccountTransaction> list = new ArrayList<>();
		for (String a : amounts) {
			AccountTransaction t = new AccountTransaction();
			t.setAmount(new BigDecimal(a));
			list.add(t);
		}
		when(accountTransactionRepository.findAllByAccountAndAmountLessThanAndTransactionDateBetween(eq(account), any(), any(), any()))
			.thenReturn(list);
	}

	private PendingTransaction givenPending(boolean debit, Account to, String amount) {
		PendingTransaction pending = new PendingTransaction();
		pending.setId(1L);
		pending.setAccount(account);
		pending.setToAccount(to);
		pending.setDebit(debit || to != null);
		pending.setAmount(new BigDecimal(amount));
		pending.setDescription("Admin posting");
		pending.setMaker(maker);
		pending.setStatus(PendingTransaction.STATUS_PENDING);
		when(pendingTransactionRepository.findById(1L)).thenReturn(Optional.of(pending));
		return pending;
	}

	private static AccountTransaction txn(String amount) {
		AccountTransaction t = new AccountTransaction();
		t.setAmount(new BigDecimal(amount));
		t.setDescription("Test");
		return t;
	}

	private static Users user(Long id) {
		Users u = new Users();
		u.setId(id);
		return u;
	}

	private static Account account(Long id, String balance, AccountType type) {
		Account a = new Account();
		a.setId(id);
		a.setName("Account " + id);
		a.setAccountType(type);
		a.setCurrentBalance(new BigDecimal(balance));
		a.setAcountTransactionList(new ArrayList<>());
		return a;
	}

	@SuppressWarnings("unused")
	private static <T> java.util.List<T> none() {
		return Collections.emptyList();
	}
}
