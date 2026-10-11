package com.pawcycle.backend.support;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/** Non-locking snapshot on an independent connection; pins pre-DELETE history, not row locks. */
public final class MysqlReadView implements AutoCloseable {
  private final Connection connection;
  private boolean closed;

  private MysqlReadView(Connection connection) {
    this.connection = connection;
  }

  public static MysqlReadView open(DataSource dataSource) throws SQLException {
    Connection connection = dataSource.getConnection();
    try {
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      connection.setReadOnly(true);
      connection.setAutoCommit(false);
      try (var query = connection.createStatement();
          var rows = query.executeQuery("SELECT COUNT(*) FROM billing_payment_methods")) {
        if (!rows.next()) throw new SQLException("Consistent read did not return a row");
      }
      return new MysqlReadView(connection);
    } catch (SQLException | RuntimeException failure) {
      try { connection.rollback(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
      try { connection.close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  public long connectionId() throws SQLException {
    try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT CONNECTION_ID()")) {
      if (!rows.next()) throw new SQLException("Connection identity is absent");
      return rows.getLong(1);
    }
  }

  @Override
  public void close() throws SQLException {
    if (closed) return;
    closed = true;
    try { connection.rollback(); }
    finally { connection.close(); }
  }
}
