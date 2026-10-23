package io.digisic.bank.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/*
 * Raised by AccountService when a debit would breach the available balance plus
 * arranged overdraft limit, the per-transaction limit, or the daily limit.
 */
@ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
public class TransactionLimitException extends RestNotAcceptableException {

	private static final long serialVersionUID = 6203538874571950121L;

	public TransactionLimitException (String message) {
		super(message);
	}

}
