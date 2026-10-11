package com.pawcycle.backend.interaction.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.TimestampJdbcType;

/** Keeps the legacy Timestamp binding independent of Hibernate's global UTC Calendar. */
final class InteractionTimestampJdbcType extends TimestampJdbcType {
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
}
