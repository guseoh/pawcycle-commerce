package com.pawcycle.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test-only SQL helper that gives fixture DML an explicit transaction without changing production
 * {@link JdbcTemplate} transaction ownership.
 */
public final class TransactionalTestSql {
  private final JdbcTemplate delegate;
  private final TransactionTemplate transaction;

  public TransactionalTestSql(
      JdbcTemplate delegate, PlatformTransactionManager transactionManager) {
    this.delegate = delegate;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  public int update(String sql, Object... arguments) {
    return Objects.requireNonNull(
        transaction.execute(status -> delegate.update(sql, arguments)));
  }

  public <T> T queryForObject(String sql, Class<T> requiredType, Object... arguments) {
    return delegate.queryForObject(sql, requiredType, arguments);
  }

  public <T> T queryForObject(
      String sql, RowMapper<T> mapper, Object... arguments) {
    return delegate.queryForObject(sql, mapper, arguments);
  }

  public List<Map<String, Object>> queryForList(String sql, Object... arguments) {
    return delegate.queryForList(sql, arguments);
  }

  public <T> List<T> queryForList(String sql, Class<T> requiredType, Object... arguments) {
    return delegate.queryForList(sql, requiredType, arguments);
  }

  public Map<String, Object> queryForMap(String sql, Object... arguments) {
    return delegate.queryForMap(sql, arguments);
  }

  public <T> List<T> query(
      String sql, RowMapper<T> mapper, Object... arguments) {
    return delegate.query(sql, mapper, arguments);
  }

  public <T> T query(
      String sql, ResultSetExtractor<T> extractor, Object... arguments) {
    return delegate.query(sql, extractor, arguments);
  }

  public void query(
      String sql, RowCallbackHandler handler, Object... arguments) {
    delegate.query(sql, handler, arguments);
  }
}
