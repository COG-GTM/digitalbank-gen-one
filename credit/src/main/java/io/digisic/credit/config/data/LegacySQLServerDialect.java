package io.digisic.credit.config.data;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.dialect.sequence.NoSequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.type.SqlTypes;

/**
 * SQL Server dialect that keeps the legacy Hibernate 5 {@code SQLServerDialect} schema and id generation:
 * <ul>
 * <li>no native sequences, so {@code hibernate_sequence}, {@code acct_no_seq} and {@code trans_no_seq} stay single-row
 * {@code next_val} tables (Hibernate 6 sequences would clash with those tables in an existing legacy database);</li>
 * <li>{@code Long} columns stay {@code numeric(19,0)}, otherwise {@code ddl-auto=update} tries to alter every legacy id
 * and foreign key column to {@code bigint}, which SQL Server rejects for key columns.</li>
 * </ul>
 */
public class LegacySQLServerDialect extends SQLServerDialect {

    public LegacySQLServerDialect() {
        super();
    }

    public LegacySQLServerDialect(DatabaseVersion version) {
        super(version);
    }

    public LegacySQLServerDialect(DialectResolutionInfo info) {
        super(info);
    }

    @Override
    protected String columnType(int sqlTypeCode) {
        return sqlTypeCode == SqlTypes.BIGINT ? "numeric(19,0)" : super.columnType(sqlTypeCode);
    }

    @Override
    public SequenceSupport getSequenceSupport() {
        return NoSequenceSupport.INSTANCE;
    }
}
