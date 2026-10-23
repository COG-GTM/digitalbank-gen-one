package io.digisic.bank.test.junit.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import io.digisic.bank.util.Constants;

/*
 * EM-135 / FND-05: overdraft must not be charged as a flat, per-debit fee, and savings
 * products must not go overdrawn. Uses only APIs that exist on master so it can be run
 * against both master (fails) and the fix branch (passes).
 */
public class OverdraftFeeStackingTest extends AccountServiceTestSupport {

	@Test
	public void repeatedDebitsWhileOverdrawnDoNotStackFees() {
		newAccount(legacyStandardChecking(), "5.00");

		debit("10.00");
		debit("10.00");

		assertTrue(transactionsOfType(Constants.ACCT_TRAN_TYPE_OVERDRAFT_FEE_CODE).isEmpty(),
				   "No flat overdraft fee transaction may be posted on debit");
		assertTrue(feeCategoryTransactions().isEmpty(), "No fee transaction may be posted on debit");
		assertEquals(2, account.getAcountTransactionList().size());
		assertEquals(0, new BigDecimal("-15.00").compareTo(account.getCurrentBalance()),
					 "Balance must reflect only the 15.00 borrowed, not 20.00 of fees");
	}

	@Test
	public void manyDebitsWhileOverdrawnNeverPostAFee() {
		newAccount(legacyStandardChecking(), "0.00");

		for (int i = 0; i < 5; i++) {
			debit("1.00");
		}

		assertTrue(transactionsOfType(Constants.ACCT_TRAN_TYPE_OVERDRAFT_FEE_CODE).isEmpty());
		assertEquals(0, new BigDecimal("-5.00").compareTo(account.getCurrentBalance()));
	}

	@Test
	public void savingsAccountCannotGoOverdrawn() {
		newAccount(legacySavings(), "5.00");

		assertThrows(RuntimeException.class, () -> debit("10.00"));

		assertEquals(0, new BigDecimal("5.00").compareTo(account.getCurrentBalance()));
		assertTrue(account.getAcountTransactionList().isEmpty());
	}

	@Test
	public void moneyMarketAccountCannotGoOverdrawn() {
		newAccount(legacyMoneyMarket(), "0.00");

		assertThrows(RuntimeException.class, () -> debit("0.01"));

		assertEquals(0, BigDecimal.ZERO.compareTo(account.getCurrentBalance()));
		assertTrue(account.getAcountTransactionList().isEmpty());
	}

	@Test
	public void savingsAccountCanStillBeDebitedToZero() {
		newAccount(legacySavings(), "5.00");

		debit("5.00");

		assertEquals(0, BigDecimal.ZERO.compareTo(account.getCurrentBalance()));
		assertEquals(1, account.getAcountTransactionList().size());
	}
}
