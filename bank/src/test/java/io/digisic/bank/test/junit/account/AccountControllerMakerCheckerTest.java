package io.digisic.bank.test.junit.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.digisic.bank.controller.AccountController;
import io.digisic.bank.model.Account;
import io.digisic.bank.model.AccountType;
import io.digisic.bank.model.PendingTransaction;
import io.digisic.bank.model.TransactionCategory;
import io.digisic.bank.model.TransactionState;
import io.digisic.bank.model.TransactionType;
import io.digisic.bank.model.security.Role;
import io.digisic.bank.model.security.Users;
import io.digisic.bank.repository.AccountRepository;
import io.digisic.bank.repository.AccountTransactionRepository;
import io.digisic.bank.repository.PendingTransactionRepository;
import io.digisic.bank.repository.TransactionCategoryRepository;
import io.digisic.bank.repository.TransactionStateRepository;
import io.digisic.bank.repository.TransactionTypeRepository;
import io.digisic.bank.service.AccountService;
import io.digisic.bank.service.UserService;

/**
 * EM-134: REST channel routes ADMIN postings and high-value payments to maker-checker.
 */
public class AccountControllerMakerCheckerTest {

	private MockMvc mvc;
	private AccountRepository accountRepository;
	private PendingTransactionRepository pendingTransactionRepository;
	private UserService userService;
	private Users admin;
	private Users customer;

	@BeforeEach
	public void setUp() {

		accountRepository = mock(AccountRepository.class);
		pendingTransactionRepository = mock(PendingTransactionRepository.class);
		AccountTransactionRepository accountTransactionRepository = mock(AccountTransactionRepository.class);
		TransactionTypeRepository transactionTypeRepository = mock(TransactionTypeRepository.class);
		TransactionStateRepository transactionStateRepository = mock(TransactionStateRepository.class);
		TransactionCategoryRepository transactionCategoryRepository = mock(TransactionCategoryRepository.class);
		userService = mock(UserService.class);

		admin = new Users();
		admin.setId(1L);
		admin.setUsername("admin@demo.io");
		customer = new Users();
		customer.setId(10L);
		customer.setUsername("customer@demo.io");
		when(userService.findByUsername("admin@demo.io")).thenReturn(admin);
		when(userService.findByUsername("customer@demo.io")).thenReturn(customer);
		when(userService.hasRole(same(admin), eq(Role.ROLE_ADMIN))).thenReturn(true);
		when(userService.hasRole(same(customer), anyString())).thenReturn(false);

		AccountType checking = new AccountType("SCK", "CHK", "Standard Checking", 0.00,
				new BigDecimal("25.00"), new BigDecimal("25.00"), new BigDecimal("10.00"));
		Account from = account(1L, "8000.00", checking, customer);
		Account to = account(2L, "0.00", checking, customer);
		when(accountRepository.findById(1L)).thenReturn(Optional.of(from));
		when(accountRepository.findById(2L)).thenReturn(Optional.of(to));

		when(transactionTypeRepository.findByCode("DPT")).thenReturn(new TransactionType("DPT", "Deposit", TransactionType.CAT_CREDIT));
		when(transactionTypeRepository.findByCode("WTH")).thenReturn(new TransactionType("WTH", "Withdrawal", TransactionType.CAT_DEBIT));
		when(transactionStateRepository.findByCode(anyString())).thenReturn(new TransactionState("COM", "Complete"));
		when(transactionCategoryRepository.findByCode(anyString())).thenReturn(new TransactionCategory("MISC", "Misc"));
		when(pendingTransactionRepository.save(any(PendingTransaction.class))).thenAnswer(i -> i.getArgument(0));

		AccountService accountService = new AccountService();
		ReflectionTestUtils.setField(accountService, "accountRepository", accountRepository);
		ReflectionTestUtils.setField(accountService, "accountTransactionRepository", accountTransactionRepository);
		ReflectionTestUtils.setField(accountService, "pendingTransactionRepository", pendingTransactionRepository);
		ReflectionTestUtils.setField(accountService, "transactionTypeRepository", transactionTypeRepository);
		ReflectionTestUtils.setField(accountService, "transactionStateRepository", transactionStateRepository);
		ReflectionTestUtils.setField(accountService, "transactionCategoryRepository", transactionCategoryRepository);

		AccountController controller = new AccountController();
		ReflectionTestUtils.setField(controller, "accountService", accountService);
		ReflectionTestUtils.setField(controller, "userService", userService);
		mvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@AfterEach
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void adminCreditIsHeldPendingAndNotPosted() throws Exception {
		loginAs("admin@demo.io");

		mvc.perform(post("/api/v1/account/1/transaction").contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": 100.00, \"transactionTypeCode\": \"DPT\", \"description\": \"Admin credit\"}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value(PendingTransaction.STATUS_PENDING))
			.andExpect(jsonPath("$.makerId").value(1));

		verify(accountRepository, never()).save(any());
		verify(pendingTransactionRepository).save(any(PendingTransaction.class));
	}

	@Test
	public void adminDebitIsHeldPendingAndNotPosted() throws Exception {
		loginAs("admin@demo.io");

		mvc.perform(post("/api/v1/account/1/transaction").contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": 100.00, \"transactionTypeCode\": \"WTH\", \"description\": \"Admin debit\"}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value(PendingTransaction.STATUS_PENDING));

		verify(accountRepository, never()).save(any());
	}

	@Test
	public void adminTransferIsHeldPendingAndNotPosted() throws Exception {
		loginAs("admin@demo.io");

		mvc.perform(post("/api/v1/account/1/transfer").contentType(MediaType.APPLICATION_JSON)
				.content("{\"toAccountId\": 2, \"amount\": 100.00}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.toAccountId").value(2));

		verify(accountRepository, never()).save(any());
	}

	@Test
	public void customerTransferAboveApprovalThresholdIsHeldPending() throws Exception {
		loginAs("customer@demo.io");

		mvc.perform(post("/api/v1/account/1/transfer").contentType(MediaType.APPLICATION_JSON)
				.content("{\"toAccountId\": 2, \"amount\": 5000.01}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value(PendingTransaction.STATUS_PENDING));

		verify(accountRepository, never()).save(any());
	}

	@Test
	public void customerDebitOverBalanceAndOverdraftReturns406() throws Exception {
		loginAs("customer@demo.io");
		accountRepository.findById(1L).get().setCurrentBalance(new BigDecimal("10.00"));

		mvc.perform(post("/api/v1/account/1/transaction").contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": 35.01, \"transactionTypeCode\": \"WTH\", \"description\": \"ATM\"}"))
			.andExpect(status().isNotAcceptable());

		verify(accountRepository, never()).save(any());
	}

	private void loginAs(String username) {
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(username, null));
	}

	private static Account account(Long id, String balance, AccountType type, Users owner) {
		Account a = new Account();
		a.setId(id);
		a.setName("Account " + id);
		a.setAccountType(type);
		a.setCurrentBalance(new BigDecimal(balance));
		a.setOwner(owner);
		a.setAcountTransactionList(new ArrayList<>());
		return a;
	}
}
