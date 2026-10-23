package io.digisic.bank.test.junit.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
import io.digisic.bank.model.TransactionCategory;
import io.digisic.bank.model.TransactionState;
import io.digisic.bank.model.TransactionType;
import io.digisic.bank.model.security.Users;
import io.digisic.bank.repository.AccountRepository;
import io.digisic.bank.repository.AccountTransactionRepository;
import io.digisic.bank.repository.TransactionCategoryRepository;
import io.digisic.bank.repository.TransactionStateRepository;
import io.digisic.bank.repository.TransactionTypeRepository;
import io.digisic.bank.service.AccountService;
import io.digisic.bank.service.UserService;

/**
 * EM-134 targeted check: REST POST /api/v1/account/{id}/transfer must refuse (406) any amount above
 * balance + arranged overdraft limit and must post nothing. Uses the real AccountService over mocked
 * repositories so the service-layer choke point is exercised.
 */
public class AccountTransferLimitTest {

	private static final String TRANSFER_URI = "/api/v1/account/1/transfer";

	private MockMvc mvc;
	private AccountRepository accountRepository;
	private Account fromAccount;
	private Account toAccount;

	@BeforeEach
	public void setUp() {

		accountRepository = mock(AccountRepository.class);
		AccountTransactionRepository accountTransactionRepository = mock(AccountTransactionRepository.class);
		TransactionTypeRepository transactionTypeRepository = mock(TransactionTypeRepository.class);
		TransactionStateRepository transactionStateRepository = mock(TransactionStateRepository.class);
		TransactionCategoryRepository transactionCategoryRepository = mock(TransactionCategoryRepository.class);
		UserService userService = mock(UserService.class);

		Users customer = new Users();
		customer.setId(10L);
		when(userService.findByUsername("customer@demo.io")).thenReturn(customer);
		when(userService.hasRole(any(), anyString())).thenReturn(false);

		// Standard Checking: overdraft limit 25.00, overdraft fee 10.00
		AccountType checking = new AccountType("SCK", "CHK", "Standard Checking", 0.00,
				new BigDecimal("25.00"), new BigDecimal("25.00"), new BigDecimal("10.00"));

		fromAccount = account(1L, "100.00", checking, customer);
		toAccount = account(2L, "50.00", checking, customer);
		when(accountRepository.findById(1L)).thenReturn(Optional.of(fromAccount));
		when(accountRepository.findById(2L)).thenReturn(Optional.of(toAccount));

		when(transactionTypeRepository.findByCode(anyString())).thenReturn(new TransactionType("TRN", "Transfer", TransactionType.CAT_EITHER));
		when(transactionStateRepository.findByCode(anyString())).thenReturn(new TransactionState("COM", "Complete"));
		when(transactionCategoryRepository.findByCode(anyString())).thenReturn(new TransactionCategory("MISC", "Misc"));
		when(accountTransactionRepository.findTop2ByAccountOrderByTransactionDateDesc(any())).thenReturn(Collections.emptyList());

		AccountService accountService = new AccountService();
		ReflectionTestUtils.setField(accountService, "accountRepository", accountRepository);
		ReflectionTestUtils.setField(accountService, "accountTransactionRepository", accountTransactionRepository);
		ReflectionTestUtils.setField(accountService, "transactionTypeRepository", transactionTypeRepository);
		ReflectionTestUtils.setField(accountService, "transactionStateRepository", transactionStateRepository);
		ReflectionTestUtils.setField(accountService, "transactionCategoryRepository", transactionCategoryRepository);

		AccountController controller = new AccountController();
		ReflectionTestUtils.setField(controller, "accountService", accountService);
		ReflectionTestUtils.setField(controller, "userService", userService);

		mvc = MockMvcBuilders.standaloneSetup(controller).build();
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("customer@demo.io", null));
	}

	@AfterEach
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void transferAboveBalancePlusOverdraftLimitReturns406AndPostsNothing() throws Exception {

		mvc.perform(post(TRANSFER_URI)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"toAccountId\": 2, \"amount\": 125.01}"))
			.andExpect(status().isNotAcceptable());

		verify(accountRepository, never()).save(any());
		Assertions.assertEquals(0, new BigDecimal("100.00").compareTo(fromAccount.getCurrentBalance()));
		Assertions.assertEquals(0, new BigDecimal("50.00").compareTo(toAccount.getCurrentBalance()));
		Assertions.assertTrue(fromAccount.getAcountTransactionList().isEmpty());
		Assertions.assertTrue(toAccount.getAcountTransactionList().isEmpty());
	}

	@Test
	public void transferExactlyAtBalancePlusOverdraftLimitIsPosted() throws Exception {

		mvc.perform(post(TRANSFER_URI)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"toAccountId\": 2, \"amount\": 125.00}"))
			.andExpect(status().isOk());

		Assertions.assertEquals(0, new BigDecimal("175.00").compareTo(toAccount.getCurrentBalance()));
		Assertions.assertEquals(1, toAccount.getAcountTransactionList().size());
	}

	private static Account account(Long id, String balance, AccountType type, Users owner) {
		Account account = new Account();
		account.setId(id);
		account.setName("Account " + id);
		account.setAccountType(type);
		account.setCurrentBalance(new BigDecimal(balance));
		account.setOwner(owner);
		account.setAcountTransactionList(new ArrayList<>());
		return account;
	}
}
