package io.digisic.bank.test.junit.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.digisic.bank.repository.RoleRepository;

/**
 * EM-141: the schema is owned by versioned Flyway migrations and Hibernate
 * never mutates it at startup.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class SchemaManagementTest {

	private static final String[] VENDORS = { "h2", "mysql", "postgresql", "sqlserver" };

	@Autowired
	private Environment environment;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RoleRepository roleRepository;

	@Test
	public void defaultProfileOnlyValidatesSchema() {
		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
	}

	@Test
	public void prodProfileDisablesHibernateSchemaManagement() throws IOException {
		Properties prod = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));
		assertThat(prod.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
	}

	@Test
	public void baselineMigrationIsVersionedForEverySupportedDatabase() throws IOException {
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		for (String vendor : VENDORS) {
			assertThat(resolver.getResources("classpath:db/migration/" + vendor + "/V1__*.sql"))
				.as("V1 baseline migration for %s", vendor)
				.hasSize(1);
		}
	}

	@Test
	public void flywayBuildsSchemaOnH2AndHibernateValidatesIt() {
		List<Map<String, Object>> applied = jdbcTemplate.queryForList(
				"select \"version\", \"success\" from \"flyway_schema_history\" where \"version\" is not null order by \"installed_rank\"");
		assertThat(applied).isNotEmpty();
		assertThat(applied.get(0).get("version")).isEqualTo("1");
		assertThat(applied).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(Boolean.TRUE));

		// Context started with ddl-auto=validate and the default data runner populated the migrated tables.
		assertThat(roleRepository.count()).isGreaterThan(0);
	}
}
