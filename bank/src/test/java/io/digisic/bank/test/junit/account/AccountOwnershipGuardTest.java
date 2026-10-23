package io.digisic.bank.test.junit.account;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.digisic.bank.model.Account;
import io.digisic.bank.model.security.Users;
import io.digisic.bank.repository.AccountTransactionRepository;
import io.digisic.bank.service.AccountService;
import io.digisic.bank.service.SampleDataService;
import io.digisic.bank.service.UserService;
import io.digisic.bank.util.Constants;

/**
 * EM-131: money movement on the web (and REST) channel must be limited to accounts
 * the authenticated user owns or co-owns.
 *
 * Uses the sample users loaded at startup: jsmith (owner) and nsmith (co-owner of the
 * joint account, non-owner of jsmith's individual account).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
public class AccountOwnershipGuardTest {

	private static final String OWNER = SampleDataService.SMPL_MALE_EMAIL;
	private static final String OTHER = SampleDataService.SMPL_FEMALE_EMAIL;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountService accountService;

	@Autowired
	private UserService userService;

	@Autowired
	private AccountTransactionRepository transactionRepository;

	// Owned by OWNER only
	private Account ownerAccount;

	// Owned by OWNER, co-owned by OTHER
	private Account jointAccount;

	// Owned by OTHER only
	private Account otherAccount;

	@BeforeEach
	public void setUp() {
		Users owner = userService.findByUsername(OWNER);
		Users other = userService.findByUsername(OTHER);

		ownerAccount = newAccount("EM-131 Owner Checking", owner, null);
		jointAccount = newAccount("EM-131 Joint Checking", owner, other);
		otherAccount = newAccount("EM-131 Other Checking", other, null);
	}

	// ---------------------------------------------------------------- deposit

	@Test
	public void webDeposit_owner_isPosted() throws Exception {
		mockMvc.perform(webDeposit(OWNER, ownerAccount, "25.00")).andExpect(status().is3xxRedirection());
		assertBalance(ownerAccount, "1025.00", 2);
	}

	@Test
	public void webDeposit_coowner_isPosted() throws Exception {
		mockMvc.perform(webDeposit(OTHER, jointAccount, "25.00")).andExpect(status().is3xxRedirection());
		assertBalance(jointAccount, "1025.00", 2);
	}

	@Test
	public void webDeposit_nonOwner_isForbiddenAndNotPosted() throws Exception {
		mockMvc.perform(webDeposit(OTHER, ownerAccount, "25.00")).andExpect(status().isForbidden());
		assertBalance(ownerAccount, "1000.00", 1);
	}

	// --------------------------------------------------------------- withdraw

	@Test
	public void webWithdraw_owner_isPosted() throws Exception {
		mockMvc.perform(webWithdraw(OWNER, ownerAccount, "40.00")).andExpect(status().is3xxRedirection());
		assertBalance(ownerAccount, "960.00", 2);
	}

	@Test
	public void webWithdraw_coowner_isPosted() throws Exception {
		mockMvc.perform(webWithdraw(OTHER, jointAccount, "40.00")).andExpect(status().is3xxRedirection());
		assertBalance(jointAccount, "960.00", 2);
	}

	@Test
	public void webWithdraw_nonOwner_isForbiddenAndNotPosted() throws Exception {
		mockMvc.perform(webWithdraw(OTHER, ownerAccount, "40.00")).andExpect(status().isForbidden());
		assertBalance(ownerAccount, "1000.00", 1);
	}

	// --------------------------------------------------------------- transfer

	@Test
	public void webTransfer_owner_isPosted() throws Exception {
		mockMvc.perform(webTransfer(OWNER, ownerAccount, jointAccount, "100.00")).andExpect(status().is3xxRedirection());
		assertBalance(ownerAccount, "900.00", 2);
		assertBalance(jointAccount, "1100.00", 2);
	}

	@Test
	public void webTransfer_coowner_isPosted() throws Exception {
		mockMvc.perform(webTransfer(OTHER, jointAccount, otherAccount, "100.00")).andExpect(status().is3xxRedirection());
		assertBalance(jointAccount, "900.00", 2);
		assertBalance(otherAccount, "1100.00", 2);
	}

	@Test
	public void webTransfer_nonOwnerFromAccount_isForbiddenAndNotPosted() throws Exception {
		mockMvc.perform(webTransfer(OTHER, ownerAccount, otherAccount, "100.00")).andExpect(status().isForbidden());
		assertBalance(ownerAccount, "1000.00", 1);
		assertBalance(otherAccount, "1000.00", 1);
	}

	@Test
	public void webTransfer_nonOwnerToAccount_isForbiddenAndNotPosted() throws Exception {
		mockMvc.perform(webTransfer(OTHER, otherAccount, ownerAccount, "100.00")).andExpect(status().isForbidden());
		assertBalance(otherAccount, "1000.00", 1);
		assertBalance(ownerAccount, "1000.00", 1);
	}

	// ------------------------------------------------- REST uses the same guard

	@Test
	public void restTransfer_coowner_isPosted() throws Exception {
		mockMvc.perform(restTransfer(OTHER, jointAccount, otherAccount, "100.00")).andExpect(status().isOk());
		assertBalance(jointAccount, "900.00", 2);
		assertBalance(otherAccount, "1100.00", 2);
	}

	@Test
	public void restTransfer_nonOwner_isForbiddenAndNotPosted() throws Exception {
		mockMvc.perform(restTransfer(OTHER, ownerAccount, otherAccount, "100.00")).andExpect(status().isForbidden());
		assertBalance(ownerAccount, "1000.00", 1);
		assertBalance(otherAccount, "1000.00", 1);
	}

	// ---------------------------------------------------------------- helpers

	private Account newAccount(String name, Users owner, Users coowner) {
		Account account = new Account();
		account.setName(name);
		account.setOwner(owner);
		account.setCoowner(coowner);
		account.setOpeningBalance(new BigDecimal("1000.00"));
		account.setAccountType(accountService.getAccoutTypeByCode(Constants.ACCT_CHK_STD_CODE));
		account.setOwnershipType(coowner == null ? accountService.getOwnershipTypeIndividual() : accountService.getOwnershipTypeJoint());
		return accountService.createNewAccount(account);
	}

	private void assertBalance(Account account, String expectedBalance, int expectedTransactions) {
		Account reloaded = accountService.getAccountById(account.getId());
		Assertions.assertEquals(0, new BigDecimal(expectedBalance).compareTo(reloaded.getCurrentBalance()),
				"balance of account " + account.getId() + " was " + reloaded.getCurrentBalance());
		Assertions.assertEquals(expectedTransactions, transactionRepository.findAllByAccount(reloaded).size(),
				"transaction count of account " + account.getId());
	}

	private static RequestPostProcessor webUser(String username) {
		return user(username).roles("USER");
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webDeposit(String username, Account account, String amount) {
		return post(Constants.URI_ACCOUNT + Constants.URI_DEPOSIT).with(webUser(username))
				.param("id", String.valueOf(account.getId()))
				.param("amount", amount);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webWithdraw(String username, Account account, String amount) {
		return post(Constants.URI_ACCOUNT + Constants.URI_WITHDRAW).with(webUser(username))
				.param("id", String.valueOf(account.getId()))
				.param("amount", amount);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webTransfer(String username, Account from, Account to, String amount) {
		return post(Constants.URI_ACCOUNT + Constants.URI_XFER_BETWEEN).with(webUser(username))
				.param("fromAccount", String.valueOf(from.getId()))
				.param("toAccount", String.valueOf(to.getId()))
				.param("amount", amount);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder restTransfer(String username, Account from, Account to, String amount) {
		return post(Constants.URI_API_ACCT_XFER, from.getId()).with(user(username).roles("API"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"toAccountId\":" + to.getId() + ",\"amount\":" + amount + "}");
	}
}
