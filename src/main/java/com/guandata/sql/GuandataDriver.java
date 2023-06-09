package com.guandata.sql;

import cc.blynk.clickhouse.ClickHouseConnection;
import cc.blynk.clickhouse.ClickHouseConnectionImpl;
import cc.blynk.clickhouse.ClickhouseJdbcUrlParser;
import cc.blynk.clickhouse.http.HttpConnector;
import cc.blynk.clickhouse.http.HttpConnectorFactory;
import cc.blynk.clickhouse.settings.ClickHouseConnectionSettings;
import cc.blynk.clickhouse.settings.ClickHouseProperties;
import cc.blynk.clickhouse.settings.ClickHouseQueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import java.util.Properties;

public class GuandataDriver implements Driver {
    private static final Logger log = LoggerFactory.getLogger(GuandataDriver.class);
    public static final GuandataDriver driver;

    static {
        driver = new GuandataDriver();
        try {
            DriverManager.registerDriver(driver);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        log.info("Driver registered");
    }

    @Override
    public ClickHouseConnection connect(String url, Properties properties) {
        return connect(url, new ClickHouseProperties(properties));
    }

    public ClickHouseConnection connect(String url, ClickHouseProperties properties) {
        if (!acceptsURL(url)) {
            return null;
        }
        HttpConnector httpConnector = HttpConnectorFactory.getHttpConnector(properties);
        return new ClickHouseConnectionImpl(httpConnector, url, properties);
    }

    @Override
    public boolean acceptsURL(String url) {
        return url.startsWith(ClickhouseJdbcUrlParser.JDBC_CLICKHOUSE_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        Properties copy = new Properties(info);
        Properties properties;
        try {
            properties = ClickhouseJdbcUrlParser.parse(url, copy).asProperties();
        } catch (Exception ex) {
            properties = copy;
            log.error("could not parse url {}", url, ex);
        }

        ClickHouseQueryParam[] querySettings = ClickHouseQueryParam.values();
        ClickHouseConnectionSettings[] connectionSettings = ClickHouseConnectionSettings.values();
        DriverPropertyInfo[] result = new DriverPropertyInfo[querySettings.length + connectionSettings.length];

        int i = 0;
        for (ClickHouseQueryParam querySetting : querySettings) {
            result[i++] = querySetting.createDriverPropertyInfo(properties);
        }

        for (ClickHouseConnectionSettings connectionSetting : connectionSettings) {
            result[i++] = connectionSetting.createDriverPropertyInfo(properties);
        }

        return result;
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
