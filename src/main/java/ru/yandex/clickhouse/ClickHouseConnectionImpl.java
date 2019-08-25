package ru.yandex.clickhouse;

import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.yandex.clickhouse.http.HttpConnector;
import ru.yandex.clickhouse.settings.ClickHouseConnectionSettings;
import ru.yandex.clickhouse.settings.ClickHouseProperties;
import ru.yandex.clickhouse.util.LogProxy;
import ru.yandex.clickhouse.util.TypeUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.Executor;


public class ClickHouseConnectionImpl implements ClickHouseConnection {
	
	private static final int DEFAULT_RESULTSET_TYPE = ResultSet.TYPE_FORWARD_ONLY;
	
    private static final Logger log = LoggerFactory.getLogger(ClickHouseConnectionImpl.class);

    private final HttpConnector httpConnector;

    private final ClickHouseProperties properties;

    private String url;

    private boolean closed = false;

    private TimeZone timezone;
    private volatile String serverVersion;

    public ClickHouseConnectionImpl(String url, ClickHouseProperties properties) {
        this.url = url;
        try {
            this.properties = ClickhouseJdbcUrlParser.parse(url, properties.asProperties());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        initTimeZone(this.properties);

        this.httpConnector = new HttpConnector(properties);
    }

    private void initTimeZone(ClickHouseProperties properties) {
        String useTimeZone = properties.getUseTimeZone();

        if (properties.isUseServerTimeZone() && !(useTimeZone == null || useTimeZone.isEmpty())) {
            throw new IllegalArgumentException(String.format("only one of %s or %s must be enabled", ClickHouseConnectionSettings.USE_SERVER_TIME_ZONE.getKey(), ClickHouseConnectionSettings.USE_TIME_ZONE.getKey()));
        }
        if (!properties.isUseServerTimeZone() && (useTimeZone == null || useTimeZone.isEmpty())) {
            throw new IllegalArgumentException(String.format("one of %s or %s must be enabled", ClickHouseConnectionSettings.USE_SERVER_TIME_ZONE.getKey(), ClickHouseConnectionSettings.USE_TIME_ZONE.getKey()));
        }
        if (properties.isUseServerTimeZone()) {
            timezone = TimeZone.getTimeZone("UTC"); // just for next query
            try (ResultSet rs = createStatement().executeQuery("select timezone()")) {
                rs.next();
                String timeZoneName = rs.getString(1);
                timezone = TimeZone.getTimeZone(timeZoneName);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else if (!(useTimeZone == null || useTimeZone.isEmpty())) {
            timezone = TimeZone.getTimeZone(useTimeZone);
        }
    }

    @Override
    public ClickHouseStatement createStatement() {
        return createStatement(DEFAULT_RESULTSET_TYPE);
    }

    public ClickHouseStatement createStatement(int resultSetType) {
        return LogProxy.wrap(ClickHouseStatement.class, new ClickHouseStatementImpl(httpclient, this, properties, resultSetType));
    }

    @Deprecated
    @Override
    public ClickHouseStatement createClickHouseStatement() throws SQLException {
        return createStatement();
    }

    @Override
    public TimeZone getTimeZone() {
        return timezone;
    }

    private ClickHouseStatement createClickHouseStatement(CloseableHttpClient httpClient) {
        return LogProxy.wrap(ClickHouseStatement.class, new ClickHouseStatementImpl(httpClient, this, properties, DEFAULT_RESULTSET_TYPE));
    }

    public PreparedStatement createPreparedStatement(String sql, int resultSetType) {
        return LogProxy.wrap(PreparedStatement.class, new ClickHousePreparedStatementImpl(httpclient, this, properties, sql, getTimeZone(), resultSetType));
    }

    public ClickHousePreparedStatement createClickHousePreparedStatement(String sql, int resultSetType) {
        return LogProxy.wrap(ClickHousePreparedStatement.class, new ClickHousePreparedStatementImpl(httpclient, this, properties, sql, getTimeZone(), resultSetType));
    }


    @Override
    public ClickHouseStatement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return createStatement(resultSetType, resultSetConcurrency, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    /**
     * lazily calculates and returns server version
     * @return server version string
     * @throws SQLException if something has gone wrong
     */
    @Override
    public String getServerVersion() throws SQLException {
        if (serverVersion == null) {
            ResultSet rs = createStatement().executeQuery("select version()");
            rs.next();
            serverVersion = rs.getString(1);
            rs.close();
        }
        return serverVersion;
    }

    @Override
    public ClickHouseStatement createStatement(int resultSetType, int resultSetConcurrency,
                                               int resultSetHoldability) throws SQLException {
        if (resultSetType == ResultSet.TYPE_SCROLL_SENSITIVE || resultSetConcurrency != ResultSet.CONCUR_READ_ONLY
            || resultSetHoldability != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
            throw new SQLFeatureNotSupportedException();
        }
        return createStatement(resultSetType);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) {
        return createPreparedStatement(sql, DEFAULT_RESULTSET_TYPE);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setAutoCommit(boolean autoCommit) {

    }

    @Override
    public boolean getAutoCommit() {
        return false;
    }

    @Override
    public void commit() {

    }

    @Override
    public void rollback() {

    }

    @Override
    public void close() throws SQLException {
        httpConnector.close();
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public DatabaseMetaData getMetaData() {
        return LogProxy.wrap(DatabaseMetaData.class, new ClickHouseDatabaseMetadata(url, this));
    }

    @Override
    public void setReadOnly(boolean readOnly) {

    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public void setCatalog(String catalog) {
        properties.setDatabase(catalog);
        URI old = URI.create(url.substring(ClickhouseJdbcUrlParser.JDBC_PREFIX.length()));
        try {
            url = ClickhouseJdbcUrlParser.JDBC_PREFIX +
                    new URI(old.getScheme(), old.getUserInfo(), old.getHost(), old.getPort(),
                            "/" + catalog, old.getQuery(), old.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getCatalog() {
        return properties.getDatabase();
    }

    @Override
    public void setTransactionIsolation(int level) {

    }

    @Override
    public int getTransactionIsolation() {
        return Connection.TRANSACTION_NONE;
    }

    @Override
    public SQLWarning getWarnings() {
        return null;
    }

    @Override
    public void clearWarnings() {

    }


    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) {
        return createPreparedStatement(sql, resultSetType);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() {
        return null;
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) {

    }

    @Override
    public void setHoldability(int holdability) {

    }

    @Override
    public int getHoldability() {
        return 0;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void rollback(Savepoint savepoint) {

    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        return createPreparedStatement(sql, resultSetType);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Clob createClob() {
        return null;
    }

    @Override
    public Blob createBlob() {
        return null;
    }

    @Override
    public NClob createNClob() {
        return null;
    }

    @Override
    public SQLXML createSQLXML() {
        return null;
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        if (timeout < 0) {
            throw new SQLException("Timeout value mustn't be less 0");
        }

        if (isClosed()) {
            return false;
        }

        return httpConnector.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) {

    }

    @Override
    public void setClientInfo(Properties properties) {

    }

    @Override
    public String getClientInfo(String name) {
        return null;
    }

    @Override
    public Properties getClientInfo() {
        return null;
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) {
        return new ClickHouseArray(TypeUtils.toSqlType(typeName), TypeUtils.isUnsigned(typeName), elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) {
        return null;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isAssignableFrom(getClass());
    }

    public void setSchema(String schema) {
        properties.setDatabase(schema);
    }

    public String getSchema() {
        return properties.getDatabase();
    }

    public void abort(Executor executor) throws SQLException {
        this.close();
    }

    public void setNetworkTimeout(Executor executor, int milliseconds) {

    }

    public int getNetworkTimeout() {
        return 0;
    }

    void cleanConnections() {
        httpConnector.cleanConnections();
    }

    String getUrl() {
        return url;
    }

    ClickHouseProperties getProperties() {
        return properties;
    }
}
