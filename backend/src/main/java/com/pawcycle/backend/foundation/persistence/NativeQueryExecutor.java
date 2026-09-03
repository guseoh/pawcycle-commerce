package com.pawcycle.backend.foundation.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Component;

/** JPA-backed native query boundary for persistence queries that are not expressible as mappings. */
@Component
public class NativeQueryExecutor {
  @FunctionalInterface
  public interface RowMapper<T> {
    T mapRow(ResultSet resultSet, int rowNumber) throws SQLException;
  }

  @FunctionalInterface
  public interface ResultSetExtractor<T> {
    T extractData(ResultSet resultSet) throws SQLException;
  }

  @FunctionalInterface
  public interface RowCallbackHandler {
    void processRow(ResultSet resultSet) throws SQLException;
  }

  private final EntityManager entityManager;

  public NativeQueryExecutor(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public int update(String sql, Object... arguments) {
    return executeUpdate(sql, arguments);
  }

  private int executeUpdate(String sql, Object[] arguments) {
    jakarta.persistence.Query query = createUpdateQuery(sql, arguments);
    return query.executeUpdate();
  }

  public <T> T queryForObject(String sql, Class<T> requiredType, Object... arguments) {
    List<Map<String, Object>> rows = rows(sql, arguments);
    requireSingleRow(rows);
    return convert(firstValue(rows.getFirst()), requiredType);
  }

  public <T> T queryForObject(String sql, RowMapper<T> mapper, Object... arguments) {
    List<Map<String, Object>> rows = rows(sql, arguments);
    requireSingleRow(rows);
    try {
      return mapper.mapRow(resultSet(rows, 0), 0);
    } catch (SQLException exception) {
      throw new IllegalStateException("Native query row mapping failed", exception);
    }
  }

  public List<Map<String, Object>> queryForList(String sql, Object... arguments) {
    return rows(sql, arguments);
  }

  public <T> List<T> queryForList(String sql, Class<T> requiredType, Object... arguments) {
    return rows(sql, arguments).stream()
        .map(row -> convert(firstValue(row), requiredType))
        .toList();
  }

  public Map<String, Object> queryForMap(String sql, Object... arguments) {
    List<Map<String, Object>> rows = rows(sql, arguments);
    requireSingleRow(rows);
    return rows.getFirst();
  }

  public <T> List<T> query(String sql, RowMapper<T> mapper, Object... arguments) {
    List<Map<String, Object>> rows = rows(sql, arguments);
    List<T> result = new ArrayList<>(rows.size());
    for (int index = 0; index < rows.size(); index++) {
      try {
        result.add(mapper.mapRow(resultSet(rows, index), index));
      } catch (SQLException exception) {
        throw new IllegalStateException("Native query row mapping failed", exception);
      }
    }
    return result;
  }

  public <T> T query(String sql, ResultSetExtractor<T> extractor, Object... arguments) {
    try {
      return extractor.extractData(resultSet(rows(sql, arguments), -1));
    } catch (SQLException exception) {
      throw new IllegalStateException("Native query extraction failed", exception);
    }
  }

  public void query(String sql, RowCallbackHandler handler, Object... arguments) {
    List<Map<String, Object>> rows = rows(sql, arguments);
    for (int index = 0; index < rows.size(); index++) {
      try {
        handler.processRow(resultSet(rows, index));
      } catch (SQLException exception) {
        throw new IllegalStateException("Native query callback failed", exception);
      }
    }
  }

  private jakarta.persistence.Query createQuery(String sql, Object[] arguments) {
    jakarta.persistence.Query query = entityManager.createNativeQuery(numbered(sql), Tuple.class);
    for (int index = 0; index < arguments.length; index++) {
      query.setParameter(index + 1, parameterValue(arguments[index]));
    }
    return query;
  }

  private jakarta.persistence.Query createUpdateQuery(String sql, Object[] arguments) {
    jakarta.persistence.Query query = entityManager.createNativeQuery(numbered(sql));
    for (int index = 0; index < arguments.length; index++) {
      query.setParameter(index + 1, parameterValue(arguments[index]));
    }
    return query;
  }

  private static Object parameterValue(Object value) {
    // Let the JPA provider bind java.time values with their declared semantics. Converting a
    // LocalDateTime through java.sql.Timestamp here makes the shared boundary dependent on the JVM
    // default timezone when the value is later interpreted as an instant.
    return value;
  }

  private static void requireSingleRow(List<?> rows) {
    if (rows.isEmpty()) {
      throw new EmptyResultDataAccessException(1);
    }
    if (rows.size() != 1) {
      throw new IncorrectResultSizeDataAccessException(1, rows.size());
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(String sql, Object[] arguments) {
    List<?> result = createQuery(sql, arguments).getResultList();
    if (result.isEmpty()) return List.of();
    List<Map<String, Object>> rows = new ArrayList<>(result.size());
    for (Object item : result) {
      if (item instanceof Tuple tuple) {
        Map<String, Object> row = new CaseInsensitiveRowMap();
        Object[] tupleValues = tuple.toArray();
        List<jakarta.persistence.TupleElement<?>> elements = tuple.getElements();
        for (int index = 0; index < tupleValues.length; index++) {
          String alias = index < elements.size() ? elements.get(index).getAlias() : null;
          String key = alias == null || alias.isBlank() ? String.valueOf(index) : alias;
          if (row.containsKey(key)) key = String.valueOf(index);
          Object value = resultValue(tupleValues[index]);
          ((CaseInsensitiveRowMap) row).addPositional(value);
          put(row, key, value);
        }
        rows.add(row);
      } else if (item instanceof Map<?, ?> map) {
        Map<String, Object> row = new CaseInsensitiveRowMap();
        map.forEach(
            (key, value) -> {
              Object normalized = resultValue(value);
              ((CaseInsensitiveRowMap) row).addPositional(normalized);
              put(row, String.valueOf(key), normalized);
            });
        rows.add(row);
      } else if (item instanceof Object[] values) {
        Map<String, Object> row = new CaseInsensitiveRowMap();
        for (int index = 0; index < values.length; index++) {
          Object value = resultValue(values[index]);
          ((CaseInsensitiveRowMap) row).addPositional(value);
          put(row, String.valueOf(index), value);
        }
        rows.add(row);
      } else {
        rows.add(Collections.singletonMap("0", resultValue(item)));
      }
    }
    return rows;
  }

  private static void put(Map<String, Object> row, String alias, Object value) {
    if (alias == null || alias.isBlank()) return;
    row.put(alias, resultValue(value));
  }

  private static Object resultValue(Object value) {
    return value instanceof LocalDate localDate ? Date.valueOf(localDate) : value;
  }

  private static Object firstValue(Map<String, Object> row) {
    return row.isEmpty() ? null : row.values().iterator().next();
  }

  @SuppressWarnings("unchecked")
  private static <T> T convert(Object value, Class<T> requiredType) {
    if (value == null) return null;
    if (requiredType.isInstance(value)) return (T) value;
    if (requiredType == Long.class) return (T) Long.valueOf(numberValue(value).longValue());
    if (requiredType == Integer.class) return (T) Integer.valueOf(numberValue(value).intValue());
    if (requiredType == Short.class) return (T) Short.valueOf(numberValue(value).shortValue());
    if (requiredType == BigDecimal.class && value instanceof Number number) {
      return (T) new BigDecimal(number.toString());
    }
    if (requiredType == Boolean.class) return (T) booleanValue(value);
    if (requiredType == String.class) return (T) value.toString();
    if (requiredType == LocalDate.class && value instanceof Date date) return (T) date.toLocalDate();
    if (requiredType == LocalDateTime.class && value instanceof Timestamp timestamp) {
      return (T) timestamp.toLocalDateTime();
    }
    if (requiredType == Instant.class && value instanceof Timestamp timestamp) {
      return (T) timestamp.toInstant();
    }
    return requiredType.cast(value);
  }

  private static ResultSet resultSet(List<Map<String, Object>> rows, int fixedIndex) {
    InvocationHandler handler = new RowResultSet(rows, fixedIndex);
    return (ResultSet)
        Proxy.newProxyInstance(
            NativeQueryExecutor.class.getClassLoader(), new Class<?>[] {ResultSet.class}, handler);
  }

  private static Number numberValue(Object value) {
    if (value instanceof Number number) return number;
    if (value instanceof String string) {
      try {
        return new BigDecimal(string.trim());
      } catch (NumberFormatException exception) {
        throw new ClassCastException(
            "Native query value is not numeric: type=String,length=" + string.length());
      }
    }
    throw new ClassCastException(
        "Native query value cannot be converted to a number: " + value.getClass().getName());
  }

  private static Boolean booleanValue(Object value) {
    if (value instanceof Boolean booleanValue) return booleanValue;
    if (value instanceof Number number) return number.intValue() != 0;
    if (value instanceof String string) {
      return "1".equals(string) || Boolean.parseBoolean(string);
    }
    return false;
  }

  private static String numbered(String sql) {
    StringBuilder result = new StringBuilder(sql.length() + 16);
    boolean quoted = false;
    int parameter = 1;
    for (int index = 0; index < sql.length(); index++) {
      char character = sql.charAt(index);
      if (character == '\'' && (index == 0 || sql.charAt(index - 1) != '\\')) quoted = !quoted;
      if (character == '?' && !quoted) result.append('?').append(parameter++);
      else result.append(character);
    }
    return result.toString();
  }

  private static final class RowResultSet implements InvocationHandler {
    private final List<Map<String, Object>> rows;
    private final int fixedIndex;
    private int cursor = -1;
    private Object lastValue;

    private RowResultSet(List<Map<String, Object>> rows, int fixedIndex) {
      this.rows = rows;
      this.fixedIndex = fixedIndex;
      if (fixedIndex >= 0) cursor = fixedIndex;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) {
      String name = method.getName();
      if (name.equals("next")) return ++cursor < rows.size();
      if (name.equals("wasNull")) return lastValue == null;
      if (name.equals("close")) return null;
      if (name.equals("isClosed")) return false;
      if (name.equals("getObject")) {
        Object value = value(arguments[0]);
        lastValue = value;
        if (arguments.length == 2 && arguments[1] instanceof Class<?> type) return convert(value, type);
        return value;
      }
      if (name.startsWith("get")) {
        Object value = value(arguments[0]);
        lastValue = value;
        return convertedGetter(name, value);
      }
      if (name.equals("toString")) return "NativeQueryExecutorResultSet";
      return defaultValue(method.getReturnType());
    }

    private Object value(Object key) {
      if (cursor < 0 || cursor >= rows.size()) return null;
      Map<String, Object> row = rows.get(cursor);
      if (key instanceof Number number) {
        int requestedIndex = number.intValue() - 1;
        if (row instanceof CaseInsensitiveRowMap positionalRow)
          return positionalRow.positionalValue(requestedIndex);
        if (requestedIndex < 0 || requestedIndex >= row.size()) return null;
        var values = row.values().iterator();
        for (int index = 0; index < requestedIndex; index++) values.next();
        return values.next();
      }
      Object value = row.get(String.valueOf(key));
      if (value != null || row.containsKey(String.valueOf(key))) return value;
      String requested = String.valueOf(key);
      return row.entrySet().stream()
          .filter(entry -> entry.getKey().equalsIgnoreCase(requested))
          .map(Map.Entry::getValue)
          .findFirst()
          .orElse(null);
    }

    private static Object convertedGetter(String method, Object value) {
      if (method.equals("getString")) return value == null ? null : value.toString();
      if (method.equals("getLong")) return value == null ? 0L : numberValue(value).longValue();
      if (method.equals("getInt")) return value == null ? 0 : numberValue(value).intValue();
      if (method.equals("getBoolean")) return value != null && booleanValue(value);
      if (method.equals("getBigDecimal")) {
        return value == null
            ? null
            : value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
      }
      if (method.equals("getDate")) {
        return value instanceof LocalDate date ? Date.valueOf(date) : value;
      }
      if (method.equals("getTimestamp")) {
        if (value instanceof LocalDateTime dateTime) return Timestamp.valueOf(dateTime);
        if (value instanceof Instant instant) return Timestamp.from(instant);
      }
      if (method.equals("getBytes")) return value instanceof byte[] bytes ? bytes : null;
      return value;
    }

    private static Object defaultValue(Class<?> type) {
      if (!type.isPrimitive()) return null;
      if (type == boolean.class) return false;
      if (type == char.class) return '\0';
      if (type == byte.class) return (byte) 0;
      if (type == short.class) return (short) 0;
      if (type == int.class) return 0;
      if (type == long.class) return 0L;
      if (type == float.class) return 0F;
      if (type == double.class) return 0D;
      return null;
    }
  }

  private static final class CaseInsensitiveRowMap extends LinkedHashMap<String, Object> {
    private final List<Object> positionalValues = new ArrayList<>();

    private void addPositional(Object value) {
      positionalValues.add(value);
    }

    private Object positionalValue(int index) {
      return index < 0 || index >= positionalValues.size() ? null : positionalValues.get(index);
    }

    @Override
    public boolean containsKey(Object key) {
      if (super.containsKey(key)) return true;
      return findKey(key) != null;
    }

    @Override
    public Object get(Object key) {
      if (super.containsKey(key)) return super.get(key);
      String matchedKey = findKey(key);
      return matchedKey == null ? null : super.get(matchedKey);
    }

    private String findKey(Object key) {
      if (key == null) return null;
      String requested = String.valueOf(key);
      return keySet().stream()
          .filter(candidate -> candidate.equalsIgnoreCase(requested))
          .findFirst()
          .orElse(null);
    }
  }
}
