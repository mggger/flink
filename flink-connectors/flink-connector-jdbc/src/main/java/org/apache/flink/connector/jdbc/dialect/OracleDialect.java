package org.apache.flink.connector.jdbc.dialect;

import org.apache.flink.connector.jdbc.internal.converter.JdbcRowConverter;
import org.apache.flink.connector.jdbc.internal.converter.OracleSQLRowConverter;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.RowType;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** JDBC dialect for Oracle. */
public class OracleDialect extends AbstractDialect {

    private static final long serialVersionUID = 1L;
    private static final String SQL_DEFAULT_PLACEHOLDER = " :";
    // Define MAX/MIN precision of TIMESTAMP type according to Mysql docs:
    // https://dev.mysql.com/doc/refman/8.0/en/fractional-seconds.html
    private static final int MAX_TIMESTAMP_PRECISION = 6;
    private static final int MIN_TIMESTAMP_PRECISION = 1;

    // Define MAX/MIN precision of DECIMAL type according to Mysql docs:
    // https://dev.mysql.com/doc/refman/8.0/en/fixed-point-types.html
    private static final int MAX_DECIMAL_PRECISION = 65;
    private static final int MIN_DECIMAL_PRECISION = 1;

    @Override
    public boolean canHandle(String url) {
        return url.startsWith("jdbc:oracle:");
    }

    @Override
    public JdbcRowConverter getRowConverter(RowType rowType) {
        return new OracleSQLRowConverter(rowType);
    }

    @Override
    public String getLimitClause(long l) {
        return null;
    }


    @Override
    public Optional<String> defaultDriverName() {
        return Optional.of("oracle.jdbc.driver.OracleDriver");
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "" + identifier + "";
    }

    /**
     * Mysql upsert query use DUPLICATE KEY UPDATE.
     *
     * <p>NOTE: It requires Mysql's primary key to be consistent with pkFields.
     *
     * <p>We don't use REPLACE INTO, if there are other fields, we can keep their previous values.
     */
    @Override
    public Optional<String> getUpsertStatement(
            String tableName, String[] fieldNames, String[] uniqueKeyFields) {

        return Optional.of(getUpsertStatement(tableName, fieldNames, uniqueKeyFields, true));
    }

    @Override
    public String getRowExistsStatement(String tableName, String[] conditionFields) {
        return super.getRowExistsStatement(tableName, conditionFields);
    }

    @Override
    public String getInsertIntoStatement(String tableName, String[] fieldNames) {
        return super.getInsertIntoStatement(tableName, fieldNames);
    }

    @Override
    public String getUpdateStatement(String tableName, String[] fieldNames, String[] conditionFields) {
        return super.getUpdateStatement(tableName, fieldNames, conditionFields);
    }

    @Override
    public String getDeleteStatement(String tableName, String[] conditionFields) {
        return super.getDeleteStatement(tableName, conditionFields);
    }

    @Override
    public String getSelectFromStatement(String tableName, String[] selectFields, String[] conditionFields) {
        return super.getSelectFromStatement(tableName, selectFields, conditionFields);
    }

    public String getUpsertStatement(
            String tableName, String[] fieldNames, String[] uniqueKeyFields, boolean allReplace) {
        StringBuilder mergeIntoSql = new StringBuilder();
        mergeIntoSql
                .append("MERGE INTO " + tableName + " T1 USING (")
                .append(buildDualQueryStatement(fieldNames))
                .append(") T2 ON (")
                .append(buildConnectionConditions(uniqueKeyFields) + ") ");

        String updateSql = buildUpdateConnection(fieldNames, uniqueKeyFields, allReplace);

        if (StringUtils.isNotEmpty(updateSql)) {
            mergeIntoSql.append(" WHEN MATCHED THEN UPDATE SET ");
            mergeIntoSql.append(updateSql);
        }

        mergeIntoSql
                .append(" WHEN NOT MATCHED THEN ")
                .append("INSERT (")
                .append(
                        Arrays.stream(fieldNames)
                                .map(col -> quoteIdentifier(col))
                                .collect(Collectors.joining(",")))
                .append(") VALUES (")
                .append(
                        Arrays.stream(fieldNames)
                                .map(col -> "T2." + quoteIdentifier(col))
                                .collect(Collectors.joining(",")))
                .append(")");

        return mergeIntoSql.toString();
    }

    private String buildUpdateConnection(
            String[] fieldNames, String[] uniqueKeyFields, boolean allReplace) {
        List<String> uniqueKeyList = Arrays.asList(uniqueKeyFields);
        String updateConnectionSql =
                Arrays.stream(fieldNames)
                        .filter(
                                col -> {
                                    boolean bbool =
                                            uniqueKeyList.contains(col.toLowerCase())
                                                    || uniqueKeyList.contains(
                                                    col.toUpperCase())
                                                    ? false
                                                    : true;
                                    return bbool;
                                })
                        .map(col -> buildConnectionByAllReplace(allReplace, col))
                        .collect(Collectors.joining(","));
        return updateConnectionSql;
    }

    private String buildConnectionByAllReplace(boolean allReplace, String col) {
        String conncetionSql =
                allReplace
                        ? quoteIdentifier("T1")
                        + "."
                        + quoteIdentifier(col)
                        + " = "
                        + quoteIdentifier("T2")
                        + "."
                        + quoteIdentifier(col)
                        : quoteIdentifier("T1")
                        + "."
                        + quoteIdentifier(col)
                        + " =nvl("
                        + quoteIdentifier("T2")
                        + "."
                        + quoteIdentifier(col)
                        + ","
                        + quoteIdentifier("T1")
                        + "."
                        + quoteIdentifier(col)
                        + ")";
        return conncetionSql;
    }

    private String buildConnectionConditions(String[] uniqueKeyFields) {
        return Arrays.stream(uniqueKeyFields)
                .map(
                        col ->
                                "T1."
                                        + quoteIdentifier(col.trim())
                                        + "=T2."
                                        + quoteIdentifier(col.trim()))
                .collect(Collectors.joining(" and "));
    }

    /**
     * build select sql , such as (SELECT ? "A",? "B" FROM DUAL)
     *
     * @param column destination column
     * @return
     */
    public String buildDualQueryStatement(String[] column) {
        StringBuilder sb = new StringBuilder("SELECT ");
        String collect =
                Arrays.stream(column)
                        .map(col -> wrapperPlaceholder(col) + quoteIdentifier(col))
                        .collect(Collectors.joining(", "));
        sb.append(collect).append(" FROM DUAL");
        return sb.toString();
    }

    /**
     * char type is wrapped with rpad.
     *
     * @param fieldName
     * @return
     */
    public String wrapperPlaceholder(String fieldName) {

        return SQL_DEFAULT_PLACEHOLDER + fieldName + " ";
    }

    @Override
    public String dialectName() {
        return "Oracle";
    }

    @Override
    public int maxDecimalPrecision() {
        return MAX_DECIMAL_PRECISION;
    }

    @Override
    public int minDecimalPrecision() {
        return MIN_DECIMAL_PRECISION;
    }

    @Override
    public int maxTimestampPrecision() {
        return MAX_TIMESTAMP_PRECISION;
    }

    @Override
    public int minTimestampPrecision() {
        return MIN_TIMESTAMP_PRECISION;
    }

    @Override
    public List<LogicalTypeRoot> unsupportedTypes() {
        // The data types used in Mysql are list at:
        // https://dev.mysql.com/doc/refman/8.0/en/data-types.html

        // TODO: We can't convert BINARY data type to
        //  PrimitiveArrayTypeInfo.BYTE_PRIMITIVE_ARRAY_TYPE_INFO in
        // LegacyTypeInfoDataTypeConverter.
        return Arrays.asList(
                LogicalTypeRoot.BINARY,
                LogicalTypeRoot.TIMESTAMP_WITH_LOCAL_TIME_ZONE,
                LogicalTypeRoot.TIMESTAMP_WITH_TIME_ZONE,
                LogicalTypeRoot.INTERVAL_YEAR_MONTH,
                LogicalTypeRoot.INTERVAL_DAY_TIME,
                LogicalTypeRoot.ARRAY,
                LogicalTypeRoot.MULTISET,
                LogicalTypeRoot.MAP,
                LogicalTypeRoot.ROW,
                LogicalTypeRoot.DISTINCT_TYPE,
                LogicalTypeRoot.STRUCTURED_TYPE,
                LogicalTypeRoot.NULL,
                LogicalTypeRoot.RAW,
                LogicalTypeRoot.SYMBOL,
                LogicalTypeRoot.UNRESOLVED);
    }
}