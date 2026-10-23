package io.digisic.bank.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
public class OverdraftNotPermittedException extends RestNotAcceptableException {

	private static final long serialVersionUID = -3587249112065314190L;

	public OverdraftNotPermittedException (String message) {
		super(message);
	}

}
