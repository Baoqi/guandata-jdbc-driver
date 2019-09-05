package cc.blynk.clickhouse;

import cc.blynk.clickhouse.util.ClickHouseValueFormatter;

import java.util.TimeZone;

public final class ClickHousePreparedStatementParameter {

    private static final ClickHousePreparedStatementParameter NULL_PARAM =
        new ClickHousePreparedStatementParameter(null, false);

    static final ClickHousePreparedStatementParameter TRUE_PARAM =
            new ClickHousePreparedStatementParameter("1", false);

    static final ClickHousePreparedStatementParameter FALSE_PARAM =
            new ClickHousePreparedStatementParameter("0", false);

    private final String stringValue;
    private final boolean quoteNeeded;

    static ClickHousePreparedStatementParameter fromObject(Object x,
        TimeZone dateTimeZone, TimeZone dateTimeTimeZone) {
        if (x == null) {
            return NULL_PARAM;
        }
        return new ClickHousePreparedStatementParameter(
            ClickHouseValueFormatter.formatObject(x, dateTimeZone, dateTimeTimeZone),
            ClickHouseValueFormatter.needsQuoting(x));
    }

    static ClickHousePreparedStatementParameter nullParameter() {
        return NULL_PARAM;
    }

    ClickHousePreparedStatementParameter(String stringValue, boolean quoteNeeded) {
        this.stringValue = stringValue == null
            ? ClickHouseValueFormatter.NULL_MARKER
            : stringValue;
        this.quoteNeeded = quoteNeeded;
    }

    String getRegularValue() {
        if (ClickHouseValueFormatter.NULL_MARKER.equals(stringValue)) {
            return "null";
        }
        if (quoteNeeded) {
            return "'" + stringValue + "'";
        }
        return stringValue;
    }

    String getBatchValue() {
        return stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }

}
