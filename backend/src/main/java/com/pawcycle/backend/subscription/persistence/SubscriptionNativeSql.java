package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.TypedParameterValue;
import org.hibernate.type.descriptor.java.LocalDateJavaType;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;
import org.hibernate.type.internal.BasicTypeImpl;

/** Only the vendor operations whose JOIN locks, atomicity or DB clock must remain explicit SQL. */
final class SubscriptionNativeSql {
  private final EntityManager entities;

  SubscriptionNativeSql(EntityManager entities) {
    this.entities = entities;
  }

  int update(String sql, Object... arguments) {
    return bind(entities.unwrap(Session.class).createNativeMutationQuery(sql), arguments).executeUpdate();
  }

  <T> NativeQuery<T> query(String sql, Class<T> result, Object... arguments) {
    var query = entities.unwrap(Session.class).createNativeQuery(sql, result);
    bind(query, arguments);
    return query;
  }

  private <T extends org.hibernate.query.CommonQueryContract> T bind(T query, Object[] arguments) {
    for (int i = 0; i < arguments.length; i++) {
      Object argument = arguments[i];
      // Native DATE predicates must use the old JDBC 4.2 LocalDate binding, without UTC Calendar.
      if (argument instanceof LocalDate date) {
        argument = new TypedParameterValue<>(
            new BasicTypeImpl<>(LocalDateJavaType.INSTANCE, LocalDateJdbcType.INSTANCE), date);
      }
      query.setParameter(i + 1, argument);
    }
    return query;
  }
}
