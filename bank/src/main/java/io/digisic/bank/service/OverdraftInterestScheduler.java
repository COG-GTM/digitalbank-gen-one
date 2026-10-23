package io.digisic.bank.service;

import java.math.BigDecimal;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.digisic.bank.model.Account;

/*
 * Accrues overdraft interest once per day on overdrawn checking accounts.
 */
@Component
@EnableScheduling
public class OverdraftInterestScheduler {
	
	private static final Logger LOG = LoggerFactory.getLogger(OverdraftInterestScheduler.class);
	
	@Autowired
	private AccountService accountService;
	
	@Scheduled(cron = "${io.digisic.bank.overdraft.interest.cron:0 0 0 * * *}")
	public void accrueDailyOverdraftInterest() {
		
		Date accrualDate = new Date();
		int charged = 0;
		
		for (Account account : accountService.getCheckingAccounts()) {
			BigDecimal interest = accountService.accrueOverdraftInterest(account, accrualDate);
			if (interest.signum() > 0) {
				charged++;
			}
		}
		
		LOG.info("Overdraft Interest: Accrued daily interest on " + charged + " overdrawn account(s).");
	}

}
