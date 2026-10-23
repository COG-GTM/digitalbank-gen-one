package io.digisic.bank.config.data;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.digisic.bank.repository.UserProfileRepository;
import io.digisic.bank.util.PiiCipher;

/**
 * Encrypts SSNs stored as plaintext before encryption at rest was enabled.
 * Runs at startup before the default and sample data loaders.
 */
@Component
public class PiiDataMigration implements CommandLineRunner, Ordered {
	
	private static final Logger LOG = LoggerFactory.getLogger(PiiDataMigration.class);
	
	@Autowired
	private UserProfileRepository userProfileRepository;
	
	@Override
	@Transactional
	public void run(String... args) {
		
		List<Object[]> rows = userProfileRepository.findPlaintextSsns();
		
		for (Object[] row : rows) {
			Long id = ((Number) row[0]).longValue();
			String ssn = (String) row[1];
			userProfileRepository.updateStoredSsn(id, PiiCipher.getInstance().encrypt(ssn));
		}
		
		if (!rows.isEmpty()) {
			LOG.info("Encrypted " + rows.size() + " plaintext SSN value(s) at rest");
		}
	}
	
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}
}
