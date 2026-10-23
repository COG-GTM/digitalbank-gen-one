package io.digisic.bank.test.junit.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.digisic.bank.exception.OverdraftNotPermittedException;
import io.digisic.bank.model.AccountTransaction;
import io.digisic.bank.model.AccountType;
import io.digisic.bank.util.Constants;

/*
 * EM-135 / FND-05: overdraft cost is a single annual interest rate on the overdrawn balance,
 * accrued daily, never a per-debit charge.
 */
public class OverdraftInterestTest extends AccountServiceTestSupport {

	private static AccountType ukChecking(String annualRate) {
		AccountType type = legacyStandardChecking();
		type.setOverdraftFee(BigDecimal.ZERO);
		type.setOverdraftInterestRate(new BigDecimal(annualRate));
		return type;
	}

	@Test
	public void dailyInterestIsAnnualRateOnOverdrawnBalance() {
		newAccount(ukChecking("36.50"), "-1000.00");

		assertEquals(new BigDecimal("1.00"), accountService.calculateDailyOverdraftInterest(account));
	}

	@Test
	public void noInterestWhenInCredit() {
		newAccount(ukChecking("39.90"), "0.00");
		assertEquals(0, BigDecimal.ZERO.compareTo(accountService.calculateDailyOverdraftInterest(account)));

		newAccount(ukChecking("39.90"), "250.00");
		assertEquals(0, BigDecimal.ZERO.compareTo(accountService.calculateDailyOverdraftInterest(account)));
	}

	@Test
	public void noInterestWhenRateNotConfigured() {
		newAccount(legacyStandardChecking(), "-500.00");

		assertEquals(0, BigDecimal.ZERO.compareTo(accountService.calculateDailyOverdraftInterest(account)));
	}

	@Test
	public void accrualPostsOneInterestChargeAndNoFlatFee() {
		newAccount(ukChecking("36.50"), "5.00");

		debit("10.00");
		debit("10.00");
		debit("10.00");

		assertTrue(transactionsOfType(Constants.ACCT_TRAN_TYPE_OVERDRAFT_FEE_CODE).isEmpty());
		assertEquals(0, new BigDecimal("-25.00").compareTo(account.getCurrentBalance()));

		BigDecimal charged = accountService.accrueOverdraftInterest(account, new Date());

		// 25.00 x 36.5% / 365 = 0.025 -> 0.03
		assertEquals(new BigDecimal("0.03"), charged);
		List<AccountTransaction> interest = transactionsOfType(Constants.ACCT_TRAN_TYPE_OVERDRAFT_CODE);
		assertEquals(1, interest.size());
		assertEquals(new BigDecimal("-0.03"), interest.get(0).getAmount());
		assertEquals(0, new BigDecimal("-25.03").compareTo(account.getCurrentBalance()));
		assertTrue(transactionsOfType(Constants.ACCT_TRAN_TYPE_OVERDRAFT_FEE_CODE).isEmpty());
	}

	@Test
	public void accrualPostsNothingWhenInCredit() {
		newAccount(ukChecking("39.90"), "100.00");

		assertEquals(0, BigDecimal.ZERO.compareTo(accountService.accrueOverdraftInterest(account, new Date())));
		assertTrue(account.getAcountTransactionList().isEmpty());
	}

	@Test
	public void savingsProductsHaveNoOverdraftLimit() {
		newAccount(legacySavings(), "100.00");
		assertEquals(0, BigDecimal.ZERO.compareTo(accountService.getOverdraftLimit(account)));

		newAccount(legacyMoneyMarket(), "100.00");
		assertEquals(0, BigDecimal.ZERO.compareTo(accountService.getOverdraftLimit(account)));

		newAccount(legacyStandardChecking(), "100.00");
		assertEquals(0, new BigDecimal("25.00").compareTo(accountService.getOverdraftLimit(account)));
	}

	@Test
	public void savingsOverdraftRejectedWithNotPermittedException() {
		newAccount(legacySavings(), "5.00");

		assertThrows(OverdraftNotPermittedException.class, () -> debit("10.00"));
	}
}
