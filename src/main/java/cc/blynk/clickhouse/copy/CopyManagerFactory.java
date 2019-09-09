package cc.blynk.clickhouse.copy;

import cc.blynk.clickhouse.ClickHouseConnection;
import cc.blynk.clickhouse.ClickHouseDataSource;

import java.sql.SQLException;

public final class CopyManagerFactory {

    private CopyManagerFactory() {
    }

    public static CopyManager create(ClickHouseConnection connection) {
        return new CopyManagerImpl(connection);
    }

    public static CopyManager create(ClickHouseDataSource dataSource) throws SQLException {
        return create(dataSource.getConnection());
    }

    public static CsvCopyManager createCsvCopyManager(ClickHouseConnection connection) {
        return new CsvCopyManagerImpl(connection);
    }

    public static CsvCopyManager createCsvCopyManager(ClickHouseDataSource dataSource) throws SQLException {
        return createCsvCopyManager(dataSource.getConnection());
    }
}
