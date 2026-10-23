package io.digisic.bank.test.junit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.digisic.bank.config.data.DefaultData;
import io.digisic.bank.model.security.Role;
import io.digisic.bank.model.security.Users;
import io.digisic.bank.repository.AccountStandingRepository;
import io.digisic.bank.repository.AccountTypeRepository;
import io.digisic.bank.repository.OwnershipTypeRepository;
import io.digisic.bank.repository.RoleRepository;
import io.digisic.bank.repository.TransactionCategoryRepository;
import io.digisic.bank.repository.TransactionStateRepository;
import io.digisic.bank.repository.TransactionTypeRepository;
import io.digisic.bank.service.UserService;

/**
 * EM-132: the bootstrap API admin password must be injected or generated, never hard-coded.
 */
public class DefaultDataAdminPasswordTest {

	private static final String LEGACY_DEFAULT = "Demo123!";

	@Test
	public void usesInjectedAdminPassword() throws Exception {
		DefaultData defaultData = newDefaultData();
		UserService userService = (UserService) ReflectionTestUtils.getField(defaultData, "userService");
		ReflectionTestUtils.setField(defaultData, "adminPassword", "injected-admin-password-for-test");

		defaultData.run();

		assertThat(createdAdmin(userService).getPassword()).isEqualTo("injected-admin-password-for-test");
	}

	@Test
	public void generatesRandomAdminPasswordWhenNotConfigured() throws Exception {
		DefaultData first = newDefaultData();
		DefaultData second = newDefaultData();

		first.run();
		second.run();

		String firstPassword = createdAdmin((UserService) ReflectionTestUtils.getField(first, "userService")).getPassword();
		String secondPassword = createdAdmin((UserService) ReflectionTestUtils.getField(second, "userService")).getPassword();

		assertThat(firstPassword).isNotEqualTo(LEGACY_DEFAULT);
		assertThat(firstPassword.length()).isGreaterThanOrEqualTo(24);
		assertThat(firstPassword).isNotEqualTo(secondPassword);
	}

	private static Users createdAdmin(UserService userService) {
		ArgumentCaptor<Users> captor = ArgumentCaptor.forClass(Users.class);
		verify(userService).createUser(captor.capture(), eq(Role.ROLE_API));
		return captor.getValue();
	}

	private static DefaultData newDefaultData() {
		DefaultData defaultData = new DefaultData();
		ReflectionTestUtils.setField(defaultData, "roleRepository", mock(RoleRepository.class));
		ReflectionTestUtils.setField(defaultData, "accountTypeRepository", mock(AccountTypeRepository.class));
		ReflectionTestUtils.setField(defaultData, "ownershipTypeRepository", mock(OwnershipTypeRepository.class));
		ReflectionTestUtils.setField(defaultData, "accountStandingRepository", mock(AccountStandingRepository.class));
		ReflectionTestUtils.setField(defaultData, "transactionTypeRepository", mock(TransactionTypeRepository.class));
		ReflectionTestUtils.setField(defaultData, "transactionStateRepository", mock(TransactionStateRepository.class));
		ReflectionTestUtils.setField(defaultData, "categoryRepository", mock(TransactionCategoryRepository.class));
		ReflectionTestUtils.setField(defaultData, "userService", mock(UserService.class));
		return defaultData;
	}
}
