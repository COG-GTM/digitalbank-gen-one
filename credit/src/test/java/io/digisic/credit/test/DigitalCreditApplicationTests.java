package io.digisic.credit.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(properties = "io.digisic.credit.api.jwt.secret=unit-test-only-signing-key-0123456789abcdef")
public class DigitalCreditApplicationTests {

	@Test
	public void contextLoads() {
	}

}
