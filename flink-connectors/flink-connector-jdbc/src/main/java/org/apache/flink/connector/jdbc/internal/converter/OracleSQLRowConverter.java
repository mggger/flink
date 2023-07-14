package org.apache.flink.connector.jdbc.internal.converter;
 
import org.apache.flink.table.types.logical.RowType;
 
public class OracleSQLRowConverter extends AbstractJdbcRowConverter {

    public OracleSQLRowConverter(RowType rowType) {
        super(rowType);
    }

    @Override
    public String converterName() {
        return "Oracle";
    }
}