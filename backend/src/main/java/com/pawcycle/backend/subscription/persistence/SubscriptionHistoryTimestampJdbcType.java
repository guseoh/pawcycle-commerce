package com.pawcycle.backend.subscription.persistence;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.TimestampJdbcType;

/**
 * Preserves the historical command-history read contract: MySQL DATETIME is extracted with
 * getTimestamp() without a Calendar, then rendered in Seoul. Hibernate's global UTC Calendar
 * changes that result in a Seoul JVM. Scope this compatibility extraction to the immutable history
 * read column; command writes and all other temporal mappings keep their existing configuration.
 */
public final class SubscriptionHistoryTimestampJdbcType extends TimestampJdbcType {
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
