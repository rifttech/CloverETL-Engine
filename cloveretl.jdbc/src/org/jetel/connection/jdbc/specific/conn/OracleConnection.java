package org.jetel.connection.jdbc.specific.conn;

import java.sql.SQLException;
import java.sql.Statement;

import org.jetel.connection.jdbc.DBConnection;
import org.jetel.connection.jdbc.specific.JdbcSpecific.AutoGeneratedKeysType;
import org.jetel.connection.jdbc.specific.JdbcSpecific.OperationType;
import org.jetel.data.Defaults;
import org.jetel.exception.JetelException;

public class OracleConnection extends DefaultConnection {
	
	private final static int ROW_PREFETCH_UNUSED = -1;

	public OracleConnection(DBConnection dbConnection,	OperationType operationType,
				AutoGeneratedKeysType autoGeneratedKeysType) throws JetelException {
		super(dbConnection, operationType, autoGeneratedKeysType);
	}

	@Override
	public Statement createStatement() throws SQLException {
		Statement stmt = super.createStatement();
		return prefetchRows(stmt);
	}

	@Override
	public Statement createStatement(int resultSetType,
			int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		Statement stmt = super.createStatement(resultSetType, resultSetConcurrency,
				resultSetHoldability);
		return prefetchRows(stmt);
	}

	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency)
			throws SQLException {
		Statement stmt = super.createStatement(resultSetType, resultSetConcurrency);
		return prefetchRows(stmt); 
	}

	/**
	 * Casting the Statement prepare to OracleStatement
     * and setting the Row Pre-fetch value as prefetchValue
	 */
	private Statement prefetchRows(Statement stmt) {
		int prefetchValue = Defaults.OracleConnection.ROW_PREFETCH;
		boolean prefetchRows = (prefetchValue != ROW_PREFETCH_UNUSED);
		
		if (prefetchRows) {
			try {
				// ((OracleStatement)stmt).setRowPrefetch(prefetchValue);
				stmt.getClass().getMethod("setRowPrefetch", int.class).invoke(stmt, prefetchValue);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		return stmt;
	}
}
