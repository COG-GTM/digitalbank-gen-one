package io.digisic.bank.test.junit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.digisic.bank.model.Account;
import io.digisic.bank.model.AccountStanding;
import io.digisic.bank.model.AccountTransaction;
import io.digisic.bank.model.AccountType;
import io.digisic.bank.model.OwnershipType;
import io.digisic.bank.model.TransactionCategory;
import io.digisic.bank.model.TransactionState;
import io.digisic.bank.model.TransactionType;
import io.digisic.bank.model.security.Users;
import io.digisic.bank.repository.AccountRepository;
import io.digisic.bank.repository.AccountStandingRepository;
import io.digisic.bank.repository.AccountTransactionRepository;
import io.digisic.bank.repository.AccountTypeRepository;
import io.digisic.bank.repository.OwnershipTypeRepository;
import io.digisic.bank.repository.TransactionCategoryRepository;
import io.digisic.bank.repository.TransactionStateRepository;
import io.digisic.bank.repository.TransactionTypeRepository;
import io.digisic.bank.service.AccountService;
import io.digisic.bank.util.Constants;

/**
 * Unit tests for the money-movement paths of <code>AccountService</code>
 * (deposit, withdraw, transfer, overdraft) plus its read/chart helpers.
 */
public class AccountServiceTest {

	private static final BigDecimal OVERDRAFT_FEE = new BigDecimal("25.00");

	@Mock private AccountRepository accountRepository;
	@Mock private AccountTypeRepository accountTypeRepository;
	@Mock private OwnershipTypeRepository ownershipTypeRepository;
	@Mock private AccountStandingRepository accountStandingRepository;
	@Mock private TransactionStateRepository transactionStateRepository;
	@Mock private TransactionTypeRepository transactionTypeRepository;
	@Mock private AccountTransactionRepository accountTransactionRepository;
	@Mock private TransactionCategoryRepository transactionCategoryRepository;

	@InjectMocks
	private AccountService accountService;

	private final Map<Long, Account> store = new HashMap<>();
	private AccountType checking;
	private AccountType savings;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.initMocks(this);

		checking = new AccountType(Constants.ACCT_CHK_STD_CODE, Constants.ACCT_CHK_CAT, "Standard Checking",
				0.0, new BigDecimal("25.00"), new BigDecimal("0.00"), OVERDRAFT_FEE);
		savings = new AccountType(Constants.ACCT_SAV_STD_CODE, Constants.ACCT_SAV_CAT, "Savings",
				0.01, new BigDecimal("100.00"), new BigDecimal("0.00"), OVERDRAFT_FEE);

		when(accountRepository.findById(anyLong()))
				.thenAnswer(inv -> Optional.ofNullable(store.get(inv.<Long>getArgument(0))));
		when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
			Account a = inv.getArgument(0);
			store.put(a.getId(), a);
			return a;
		});
		when(transactionTypeRepository.findByCode(anyString()))
				.thenAnswer(inv -> new TransactionType(inv.getArgument(0), inv.getArgument(0), "T"));
		when(transactionCategoryRepository.findByCode(anyString()))
				.thenAnswer(inv -> new TransactionCategory(inv.getArgument(0), inv.getArgument(0)));
		when(transactionStateRepository.findByCode(anyString()))
				.thenAnswer(inv -> new TransactionState(inv.getArgument(0), inv.getArgument(0)));
		when(accountStandingRepository.findByCode(anyString()))
				.thenAnswer(inv -> new AccountStanding(inv.getArgument(0), inv.getArgument(0)));
	}

	private Account account(long id, String balance, AccountType type) {
		Account a = new Account();
		a.setId(id);
		a.setName("Account " + id);
		a.setAccountType(type);
		a.setCurrentBalance(new BigDecimal(balance));
		a.setOpeningBalance(new BigDecimal(balance));
		a.setAcountTransactionList(new ArrayList<>());
		store.put(id, a);
		return a;
	}

	private AccountTransaction txn(String amount) {
		AccountTransaction t = new AccountTransaction();
		t.setAmount(new BigDecimal(amount));
		t.setDescription("test");
		t.setTransactionDate(new Date());
		return t;
	}

	private AccountTransaction txn(String amount, String categoryCode) {
		AccountTransaction t = txn(amount);
		t.setTransactionCategory(new TransactionCategory(categoryCode, categoryCode));
		return t;
	}

	// ---- deposit ----

	@Test
	public void depositIncreasesBalanceAndRecordsCompletedTransaction() {
		Account acct = account(1L, "100.00", checking);

		accountService.creditTransaction(acct, txn("50.25"));

		Account saved = store.get(1L);
		assertEquals(new BigDecimal("150.25"), saved.getCurrentBalance());
		assertEquals(1, saved.getAcountTransactionList().size());
		AccountTransaction recorded = saved.getAcountTransactionList().get(0);
		assertEquals(new BigDecimal("50.25"), recorded.getAmount());
		assertEquals(new BigDecimal("150.25"), recorded.getRunningBalance());
		assertEquals(Constants.ACCT_TRAN_ST_COMP_CODE, recorded.getTransactionState().getCode());
		assertEquals(Constants.ACCT_TRAN_CAT_INC_CODE, recorded.getTransactionCategory().getCode());
		assertSame(saved, recorded.getAccount());
	}

	@Test
	public void depositKeepsExplicitCategoryAndDefaultsMissingDate() {
		Account acct = account(1L, "10.00", checking);
		AccountTransaction t = txn("5.00", Constants.ACCT_TRAN_CAT_GFT_CODE);
		t.setTransactionDate(null);

		accountService.creditTransaction(acct, t);

		AccountTransaction recorded = store.get(1L).getAcountTransactionList().get(0);
		assertEquals(Constants.ACCT_TRAN_CAT_GFT_CODE, recorded.getTransactionCategory().getCode());
		assertNotNull(recorded.getTransactionDate());
	}

	@Test
	public void firstDepositOnNewAccountDoesNotDoubleCountOpeningBalance() {
		Account acct = account(1L, "100.00", checking);
		acct.setAcountTransactionList(null);

		accountService.creditTransaction(acct, txn("100.00"));

		assertEquals(new BigDecimal("100.00"), store.get(1L).getCurrentBalance());
		assertEquals(1, store.get(1L).getAcountTransactionList().size());
	}

	// ---- withdraw ----

	@Test
	public void withdrawWithinBalanceDecreasesBalanceWithoutFee() {
		Account acct = account(1L, "100.00", checking);

		accountService.debitTransaction(acct, txn("40.00"));

		Account saved = store.get(1L);
		assertEquals(new BigDecimal("60.00"), saved.getCurrentBalance());
		assertEquals(1, saved.getAcountTransactionList().size());
		AccountTransaction recorded = saved.getAcountTransactionList().get(0);
		assertEquals(new BigDecimal("-40.00"), recorded.getAmount());
		assertEquals(new BigDecimal("60.00"), recorded.getRunningBalance());
		assertEquals(Constants.ACCT_TRAN_CAT_MISC_CODE, recorded.getTransactionCategory().getCode());
		assertEquals(Constants.ACCT_TRAN_ST_COMP_CODE, recorded.getTransactionState().getCode());
	}

	@Test
	public void withdrawOfExactBalanceIsNotAnOverdraft() {
		Account acct = account(1L, "100.00", checking);
		AccountTransaction t = txn("100.00", Constants.ACCT_TRAN_CAT_SHP_CODE);
		t.setTransactionDate(null);

		accountService.debitTransaction(acct, t);

		Account saved = store.get(1L);
		assertEquals(0, BigDecimal.ZERO.compareTo(saved.getCurrentBalance()));
		assertEquals(1, saved.getAcountTransactionList().size());
		assertEquals(Constants.ACCT_TRAN_CAT_SHP_CODE,
				saved.getAcountTransactionList().get(0).getTransactionCategory().getCode());
		assertNotNull(saved.getAcountTransactionList().get(0).getTransactionDate());
	}

	// ---- overdraft ----

	@Test
	public void withdrawBeyondBalanceAppliesOverdraftFeeTransaction() {
		Account acct = account(1L, "100.00", checking);

		accountService.debitTransaction(acct, txn("150.00"));

		Account saved = store.get(1L);
		assertEquals(new BigDecimal("-75.00"), saved.getCurrentBalance());
		List<AccountTransaction> list = saved.getAcountTransactionList();
		assertEquals(2, list.size());
		AccountTransaction fee = list.get(1);
		assertEquals(OVERDRAFT_FEE.negate(), fee.getAmount());
		assertEquals(new BigDecimal("-75.00"), fee.getRunningBalance());
		assertEquals(Constants.ACCT_TRAN_CAT_FEE_CODE, fee.getTransactionCategory().getCode());
		assertEquals(Constants.ACCT_TRAN_TYPE_OVERDRAFT_FEE_CODE, fee.getTransactionType().getCode());
		assertTrue(fee.getTransactionDate().after(list.get(0).getTransactionDate()));
	}

	// ---- transfer ----

	@Test
	public void transferMovesFundsBetweenAccounts() {
		Account from = account(1L, "500.00", checking);
		Account to = account(2L, "20.00", savings);

		accountService.transfer(from, to, txn("125.50"));

		assertEquals(new BigDecimal("374.50"), store.get(1L).getCurrentBalance());
		assertEquals(new BigDecimal("145.50"), store.get(2L).getCurrentBalance());

		AccountTransaction out = store.get(1L).getAcountTransactionList().get(0);
		AccountTransaction in = store.get(2L).getAcountTransactionList().get(0);
		assertEquals(new BigDecimal("-125.50"), out.getAmount());
		assertEquals(new BigDecimal("125.50"), in.getAmount());
		assertEquals(Constants.ACCT_TRAN_TYPE_XFER_CODE, out.getTransactionType().getCode());
		assertEquals(Constants.ACCT_TRAN_TYPE_XFER_CODE, in.getTransactionType().getCode());
		assertTrue(out.getDescription().startsWith("Transfer to Account"));
		assertTrue(in.getDescription().startsWith("Transfer from Account"));
	}

	@Test
	public void transferExceedingSourceBalanceChargesOverdraftOnSourceOnly() {
		Account from = account(1L, "50.00", checking);
		Account to = account(2L, "0.00", savings);

		accountService.transfer(from, to, txn("80.00"));

		assertEquals(new BigDecimal("-55.00"), store.get(1L).getCurrentBalance());
		assertEquals(2, store.get(1L).getAcountTransactionList().size());
		assertEquals(new BigDecimal("80.00"), store.get(2L).getCurrentBalance());
		assertEquals(1, store.get(2L).getAcountTransactionList().size());
	}

	// ---- account lifecycle ----

	@Test
	public void createNewAccountSetsDefaultsAndPostsOpeningDeposit() {
		Account acct = new Account();
		acct.setId(9L);
		acct.setName("New");
		acct.setAccountType(savings);
		acct.setOpeningBalance(new BigDecimal("250.00"));

		Account created = accountService.createNewAccount(acct);

		assertEquals(new BigDecimal("250.00"), created.getCurrentBalance());
		assertEquals(0.01, created.getInterestRate());
		assertEquals(Constants.ACCT_STND_OPEN_CODE, created.getAccountStanding().getCode());
		assertNotNull(created.getDateOpened());
		Account saved = store.get(9L);
		assertEquals(new BigDecimal("250.00"), saved.getCurrentBalance());
		assertEquals(1, saved.getAcountTransactionList().size());
		assertEquals(Constants.ACCT_TRAN_TYPE_DEPOSIT_CODE,
				saved.getAcountTransactionList().get(0).getTransactionType().getCode());
	}

	@Test
	public void createNewAccountKeepsProvidedOpenDate() {
		Date opened = new Date(0L);
		Account acct = new Account();
		acct.setId(10L);
		acct.setAccountType(checking);
		acct.setOpeningBalance(new BigDecimal("30.00"));
		acct.setDateOpened(opened);

		assertEquals(opened, accountService.createNewAccount(acct).getDateOpened());
	}

	@Test
	public void updateSaveAndDeleteDelegateToRepository() {
		Account acct = account(3L, "1.00", checking);
		Users user = new Users("jsmith", "pw");

		assertSame(acct, accountService.updateAccount(acct));
		assertSame(acct, accountService.save(acct));
		accountService.deleteAccount(acct);
		accountService.deleteAllAccounts(user);

		verify(accountRepository).delete(acct);
		verify(accountRepository).deleteByOwner(user);
	}

	@Test
	public void lookupsReturnNullWhenMissing() {
		when(accountTransactionRepository.findById(anyLong())).thenReturn(Optional.empty());

		assertNull(accountService.getAccountById(404L));
		assertNull(accountService.getAccountTransactionById(404L));

		AccountTransaction t = txn("1.00");
		when(accountTransactionRepository.findById(7L)).thenReturn(Optional.of(t));
		assertSame(t, accountService.getAccountTransactionById(7L));
	}

	@Test
	public void accountCategoryHelpers() {
		Account chk = account(1L, "0.00", checking);
		Account sav = account(2L, "0.00", savings);

		assertTrue(accountService.isCheckingAccount(chk));
		assertFalse(accountService.isSavingsAccount(chk));
		assertTrue(accountService.isSavingsAccount(sav));
		assertFalse(accountService.isCheckingAccount(sav));
	}

	@Test
	public void userAccountQueriesMergeOwnedAndCoOwnedAccounts() {
		Users user = new Users("jsmith", "pw");
		Account owned = account(1L, "0.00", checking);
		Account joint = account(2L, "0.00", savings);
		when(accountRepository.findByOwner(user)).thenAnswer(i -> new ArrayList<>(Arrays.asList(owned)));
		when(accountRepository.findByCoowner(user)).thenAnswer(i -> new ArrayList<>(Arrays.asList(joint)));
		when(accountRepository.findByOwnerAndAccountType_Category(user, Constants.ACCT_CHK_CAT))
				.thenAnswer(i -> new ArrayList<>(Arrays.asList(owned)));
		when(accountRepository.findByCoownerAndAccountType_Category(user, Constants.ACCT_CHK_CAT))
				.thenAnswer(i -> new ArrayList<>());
		when(accountRepository.findByOwnerAndAccountType_Category(user, Constants.ACCT_SAV_CAT))
				.thenAnswer(i -> new ArrayList<>());
		when(accountRepository.findByCoownerAndAccountType_Category(user, Constants.ACCT_SAV_CAT))
				.thenAnswer(i -> new ArrayList<>(Arrays.asList(joint)));

		assertEquals(Arrays.asList(owned, joint), accountService.getAllAccounts(user));
		assertEquals(Arrays.asList(owned, joint), accountService.getAllAccountsUser(user));
		assertEquals(Arrays.asList(owned), accountService.getCheckingAccounts(user));
		assertEquals(Arrays.asList(joint), accountService.getSavingsAccounts(user));
	}

	@Test
	public void referenceDataGettersDelegateToRepositories() {
		List<Account> all = Collections.singletonList(account(1L, "0.00", checking));
		when(accountRepository.findAll()).thenReturn(all);
		when(accountRepository.findByAccountType_Category(Constants.ACCT_CHK_CAT)).thenReturn(all);
		when(accountRepository.findByAccountType_Category(Constants.ACCT_SAV_CAT)).thenReturn(Collections.emptyList());
		OwnershipType ind = new OwnershipType(Constants.ACCT_OWN_IND_CODE, "Individual");
		OwnershipType jnt = new OwnershipType(Constants.ACCT_OWN_JNT_CODE, "Joint");
		when(ownershipTypeRepository.findByCode(Constants.ACCT_OWN_IND_CODE)).thenReturn(ind);
		when(ownershipTypeRepository.findByCode(Constants.ACCT_OWN_JNT_CODE)).thenReturn(jnt);
		when(ownershipTypeRepository.findAll()).thenReturn(Arrays.asList(ind, jnt));
		when(accountTypeRepository.findByCode(Constants.ACCT_CHK_STD_CODE)).thenReturn(checking);
		when(accountTypeRepository.findAll()).thenReturn(Arrays.asList(checking, savings));
		when(accountTypeRepository.findByCategory(Constants.ACCT_CHK_CAT)).thenReturn(Arrays.asList(checking));
		when(accountTypeRepository.findByCategory(Constants.ACCT_SAV_CAT)).thenReturn(Arrays.asList(savings));
		when(accountStandingRepository.findAll()).thenReturn(Collections.emptyList());
		when(transactionStateRepository.findAll()).thenReturn(Collections.emptyList());
		when(transactionTypeRepository.findAll()).thenReturn(Collections.emptyList());
		when(transactionCategoryRepository.findAll()).thenReturn(Collections.emptyList());
		Account acct = all.get(0);
		AccountTransaction latest = txn("1.00");
		when(accountTransactionRepository.findTopByAccountOrderByTransactionDateDesc(acct)).thenReturn(latest);
		when(accountTransactionRepository.findTop2ByAccountOrderByTransactionDateDesc(acct))
				.thenReturn(Arrays.asList(latest));

		assertEquals(all, accountService.getAllAccounts());
		assertEquals(all, accountService.getCheckingAccounts());
		assertTrue(accountService.getSavingsAccounts().isEmpty());
		assertSame(ind, accountService.getOwnershipTypeIndividual());
		assertSame(jnt, accountService.getOwnershipTypeJoint());
		assertSame(jnt, accountService.getOwnershipTypeByCode(Constants.ACCT_OWN_JNT_CODE));
		assertEquals(2, accountService.getOwnershipTypes().size());
		assertSame(checking, accountService.getAccoutTypeByCode(Constants.ACCT_CHK_STD_CODE));
		assertEquals(2, accountService.getAccountTypes().size());
		assertEquals(Arrays.asList(checking), accountService.getCheckingAccountTypes());
		assertEquals(Arrays.asList(savings), accountService.getSavingsAccountTypes());
		assertTrue(accountService.getAccountStanding().isEmpty());
		assertTrue(accountService.getTransactionState().isEmpty());
		assertTrue(accountService.getTransactionType().isEmpty());
		assertTrue(accountService.getTransactionCategory().isEmpty());
		assertEquals(Constants.ACCT_TRAN_TYPE_ATM_CODE,
				accountService.getTransactionTypeByCode(Constants.ACCT_TRAN_TYPE_ATM_CODE).getCode());
		assertEquals(Constants.ACCT_TRAN_CAT_FDD_CODE,
				accountService.getTransactionCategoryByCode(Constants.ACCT_TRAN_CAT_FDD_CODE).getCode());
		assertSame(latest, accountService.getLatestAccountTransaction(acct));
		assertEquals(1, accountService.getLastTwoAccountTransactions(acct).size());
	}

	// ---- dashboard chart data ----

	@Test
	public void balanceSummaryChartListsEachAccountBalance() {
		Users user = new Users("jsmith", "pw");
		Account a = account(1L, "10.50", checking);
		Account b = account(2L, "99.00", savings);
		when(accountRepository.findByOwner(user)).thenAnswer(i -> new ArrayList<>(Arrays.asList(a)));
		when(accountRepository.findByCoowner(user)).thenAnswer(i -> new ArrayList<>(Arrays.asList(b)));

		List<List<String>> data = accountService.getChartDataAccountBalanceSummary(user);

		assertEquals(Arrays.asList("Account 1", "Account 2"), data.get(0));
		assertEquals(Arrays.asList("10.50", "99.00"), data.get(1));
	}

	@Test
	public void creditVsDebitChartSumsLastThreeMonths() {
		Users user = new Users("jsmith", "pw");
		Account a = account(1L, "0.00", checking);
		when(accountRepository.findByOwner(user)).thenAnswer(i -> new ArrayList<>(Arrays.asList(a)));
		when(accountRepository.findByCoowner(user)).thenAnswer(i -> new ArrayList<>());
		when(accountTransactionRepository.findAllByAccountAndAmountGreaterThanAndTransactionDateAfter(eq(a), any(), any()))
				.thenReturn(Arrays.asList(txn("100.00"), txn("50.00")));
		when(accountTransactionRepository.findAllByAccountAndAmountLessThanAndTransactionDateAfter(eq(a), any(), any()))
				.thenReturn(Arrays.asList(txn("-30.00")));

		List<List<String>> data = accountService.getChartDataCreditVsDebit(user);

		assertEquals(Arrays.asList("Account 1"), data.get(0));
		assertEquals(Arrays.asList("150.00"), data.get(1));
		assertEquals(Arrays.asList("30.00"), data.get(2));
	}

	@Test
	public void transactionByCategoryChartComputesPercentages() {
		Users user = new Users("jsmith", "pw");
		Account a = account(1L, "0.00", checking);
		TransactionCategory income = new TransactionCategory(Constants.ACCT_TRAN_CAT_INC_CODE, "Income");
		TransactionCategory food = new TransactionCategory(Constants.ACCT_TRAN_CAT_FDD_CODE, "Food");
		TransactionCategory travel = new TransactionCategory(Constants.ACCT_TRAN_CAT_TRV_CODE, "Travel");
		TransactionCategory empty = new TransactionCategory(Constants.ACCT_TRAN_CAT_TAX_CODE, "Tax");
		when(transactionCategoryRepository.findAll()).thenReturn(Arrays.asList(income, food, travel, empty));
		when(accountRepository.findByOwner(user)).thenAnswer(i -> new ArrayList<>(Arrays.asList(a)));
		when(accountRepository.findByCoowner(user)).thenAnswer(i -> new ArrayList<>());
		when(accountTransactionRepository.findAllByAccountAndTransactionCategoryAndTransactionDateAfter(eq(a), eq(income), any()))
				.thenReturn(Arrays.asList(txn("200.00")));
		when(accountTransactionRepository.findAllByAccountAndTransactionCategoryAndTransactionDateAfter(eq(a), eq(food), any()))
				.thenReturn(Arrays.asList(txn("-30.00"), txn("-10.00")));
		when(accountTransactionRepository.findAllByAccountAndTransactionCategoryAndTransactionDateAfter(eq(a), eq(travel), any()))
				.thenReturn(Arrays.asList(txn("-60.00")));
		when(accountTransactionRepository.findAllByAccountAndTransactionCategoryAndTransactionDateAfter(eq(a), eq(empty), any()))
				.thenReturn(Collections.emptyList());

		List<List<String>> data = accountService.getChartDataTransactionByCategory(user);

		assertEquals(Arrays.asList("Food", "Travel"), data.get(0));
		assertEquals(Arrays.asList("40.00", "60.00"), data.get(1));
		assertEquals(2, data.get(2).size());
		assertEquals(Arrays.asList("Income"), data.get(3));
		assertEquals(Arrays.asList("100.00"), data.get(4));
		assertEquals(1, data.get(5).size());
	}
}
