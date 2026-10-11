package com.pawcycle.backend.commerce.operations.persistence;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.TimestampJdbcType;

/** Preserve the legacy getTimestamp-without-Calendar contract for the UNION projection only. */
public final class OperationsTimestampJdbcType extends TimestampJdbcType {
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
