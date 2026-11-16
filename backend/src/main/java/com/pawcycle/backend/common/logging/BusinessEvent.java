package com.pawcycle.backend.common.logging;

import org.slf4j.event.KeyValuePair;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One business event log line: a fixed event name plus allowlisted fields. Free text cannot be
 * logged: field names come from {@link Field}, numbers are written as they are, and a text value
 * that is not a short code ({@code [A-Za-z0-9_.:-]{1,64}}) is replaced by {@code INVALID}. So names,
 * addresses, e-mails, payment keys in free text and provider bodies cannot reach the log through
 * this class.
 *
 * <p>Text logs read {@code event=payment.confirm.finished paymentId=7 outcome=SUCCEEDED}; JSON logs
 * also carry {@code businessEvent} and each field as key-value members.
 */
public final class BusinessEvent {
  static final String LOGGER = "pawcycle.business-event";
  static final String INVALID = "INVALID";
  private static final Logger log = LoggerFactory.getLogger(LOGGER);
  private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

  /** Event names. Fixed values keep log queries stable. */
  public enum Name {
    PAYMENT_CONFIRM_FINISHED("payment.confirm.finished"),
    PAYMENT_EXPIRED("payment.expired"),
    PAYMENT_RECONCILED("payment.reconciled"),
    PAYMENT_RECOVERY_FINISHED("payment.recovery.finished"),
    REFUND_FINISHED("refund.finished"),
    BILLING_CHARGE_FINISHED("billing.charge.finished"),
    SUBSCRIPTION_ORDER_PROCESSED("subscription.order.processed"),
    ADMIN_ACTION("admin.action");

    private final String value;

    Name(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  /** The only field names a business event may carry: internal ids and short status codes. */
  public enum Field {
    PAYMENT_ID("paymentId"),
    ORDER_ID("orderId"),
    REFUND_ID("refundId"),
    SUBSCRIPTION_ID("subscriptionId"),
    SCHEDULE_ID("scheduleId"),
    ADMIN_ID("adminId"),
    TARGET_TYPE("targetType"),
    TARGET_ID("targetId"),
    ACTION("action"),
    OUTCOME("outcome"),
    PROVIDER_STATUS("providerStatus"),
    PREVIOUS_STATUS("previousStatus");

    private final String key;

    Field(String key) {
      this.key = key;
    }

    public String key() {
      return key;
    }
  }

  private final Name name;
  /** Insertion order, so a line reads in the order the code added the fields. */
  private final Map<Field, Object> fields = new LinkedHashMap<>();

  private BusinessEvent(Name name) {
    this.name = name;
  }

  public static BusinessEvent of(Name name) {
    return new BusinessEvent(name);
  }

  public BusinessEvent with(Field field, long value) {
    fields.put(field, value);
    return this;
  }

  /** A short code such as a status; anything else is logged as {@code INVALID}. Null is skipped. */
  public BusinessEvent with(Field field, String value) {
    if (value != null) fields.put(field, CODE.matcher(value).matches() ? value : INVALID);
    return this;
  }

  /**
   * Logs after the surrounding transaction commits, so rolled-back work is never reported; logs
   * immediately when no transaction synchronization is active. Nothing is logged on rollback.
   */
  public void log() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          write();
        }
      });
    } else {
      write();
    }
  }

  void write() {
    LoggingEventBuilder builder = log.atInfo();
    for (KeyValuePair pair : keyValues()) builder = builder.addKeyValue(pair.key, pair.value);
    builder.log(text());
  }

  /** {@code event=<name> key=value ...} in the order the fields were added. */
  String text() {
    StringJoiner text = new StringJoiner(" ");
    text.add("event=" + name.value());
    fields.forEach((field, value) -> text.add(field.key() + "=" + value));
    return text.toString();
  }

  /** JSON members. "businessEvent", not "event": ECS reserves event.* for its own object. */
  List<KeyValuePair> keyValues() {
    List<KeyValuePair> pairs = new ArrayList<>();
    pairs.add(new KeyValuePair("businessEvent", name.value()));
    fields.forEach((field, value) -> pairs.add(new KeyValuePair(field.key(), value)));
    return pairs;
  }

}
