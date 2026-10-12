package com.pawcycle.backend.commerce.notification.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.TimestampJdbcType;

/** Keeps the legacy Timestamp binding independent of Hibernate's global UTC Calendar. */
final class NotificationTimestampJdbcType extends TimestampJdbcType {
  @Override
  public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
    return new BasicBinder<>(javaType, this) {
      @Override
      protected void doBind(PreparedStatement statement, X value, int index, WrapperOptions options)
          throws SQLException {
        statement.setTimestamp(index, javaType.unwrap(value, Timestamp.class, options));
      }
      @Override
      protected void doBind(CallableStatement statement, X value, String name, WrapperOptions options)
          throws SQLException {
        statement.setTimestamp(name, javaType.unwrap(value, Timestamp.class, options));
      }
    };
  }
  @Override
  public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
    return new BasicExtractor<>(javaType, this) {
      @Override
      protected X doExtract(ResultSet rs, int index, WrapperOptions options) throws SQLException {
        return javaType.wrap(rs.getTimestamp(index), options);
      }

      @Override
      protected X doExtract(CallableStatement statement, int index, WrapperOptions options)
          throws SQLException {
        return javaType.wrap(statement.getTimestamp(index), options);
      }

      @Override
      protected X doExtract(CallableStatement statement, String name, WrapperOptions options)
          throws SQLException {
        return javaType.wrap(statement.getTimestamp(name), options);
      }
    };
  }
}
