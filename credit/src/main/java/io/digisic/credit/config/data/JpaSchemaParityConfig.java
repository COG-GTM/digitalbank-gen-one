package io.digisic.credit.config.data;

import java.util.Map;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps the generated schema and id numbering identical to the legacy Hibernate 5.3 build.
 * <ul>
 * <li>Hibernate 6 also turns {@code @NotEmpty}/{@code @NotBlank} into NOT NULL columns, Hibernate 5.3 only did that for
 * {@code @NotNull}. Columns that must be NOT NULL declare it explicitly with {@code @Column(nullable = false)}.</li>
 * <li>The deploy descriptors and documented profiles still name Hibernate 5 dialects. The MySQL 5.x dialects no longer
 * exist in Hibernate 6, and Hibernate 6's {@code SQLServerDialect} switches id generation to native sequences, so those
 * names are mapped to their Hibernate 6 equivalents.</li>
 * </ul>
 */
@Configuration
public class JpaSchemaParityConfig implements HibernatePropertiesCustomizer {

    static final String DIALECT = "hibernate.dialect";

    private static final Map<String, String> LEGACY_DIALECTS = Map.of(
            "org.hibernate.dialect.MySQL5Dialect", "org.hibernate.dialect.MySQLDialect",
            "org.hibernate.dialect.MySQL55Dialect", "org.hibernate.dialect.MySQLDialect",
            "org.hibernate.dialect.MySQL57Dialect", "org.hibernate.dialect.MySQLDialect",
            "org.hibernate.dialect.SQLServerDialect", LegacySQLServerDialect.class.getName());

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.putIfAbsent("hibernate.validator.apply_to_ddl", "false");

        Object dialect = hibernateProperties.get(DIALECT);
        if (dialect instanceof String name && LEGACY_DIALECTS.containsKey(name.trim())) {
            hibernateProperties.put(DIALECT, LEGACY_DIALECTS.get(name.trim()));
        }
    }
}
