package com.pawcycle.backend.member.address.application;

import com.pawcycle.backend.commerce.AddressRequest;
import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.CommerceRowResponse;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberAddressApplicationService {
  private final NativeQueryExecutor jdbc;
  private final Clock clock;

  public MemberAddressApplicationService(NativeQueryExecutor jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<CommerceRowResponse> list(long memberId) {
    return CommerceRowResponse.from(
        jdbc.queryForList(
            """
            SELECT address.id AS addressId,address.name,address.recipient_name AS recipientName,address.recipient_phone AS recipientPhone,
            address.postal_code AS postalCode,address.address_line1 AS addressLine1,address.address_line2 AS addressLine2,
            (member.default_address_id=address.id) AS isDefault
            FROM member_addresses address JOIN members member ON member.id=address.member_id
            WHERE address.member_id=? ORDER BY address.id\
            """,
            memberId));
  }

  @Transactional
  public long create(long memberId, AddressRequest request) {
    validate(request, true);
    jdbc.update(
        "INSERT INTO"
            + " member_addresses(member_id,name,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,updated_at)"
            + " VALUES (?,?,?,?,?,?,?,?,?)",
        memberId,
        request.name(),
        request.recipientName(),
        request.recipientPhone(),
        request.postalCode(),
        request.addressLine1(),
        request.addressLine2(),
        now(),
        now());
    long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    Integer defaults =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM members WHERE id=? AND default_address_id IS NOT NULL", Integer.class, memberId);
    if (defaults == 0)
      jdbc.update("UPDATE members SET default_address_id=? WHERE id=?", id, memberId);
    releaseAddressHolds(memberId);
    return id;
  }

  @Transactional
  public void update(long memberId, long addressId, AddressRequest request) {
    validate(request, true);
    ensureOwnership(memberId, addressId);
    jdbc.update(
        "UPDATE member_addresses SET"
            + " name=?,recipient_name=?,recipient_phone=?,postal_code=?,address_line1=?,address_line2=?,updated_at=?"
            + " WHERE id=?",
        request.name(),
        request.recipientName(),
        request.recipientPhone(),
        request.postalCode(),
        request.addressLine1(),
        request.addressLine2(),
        now(),
        addressId);
    releaseAddressHolds(memberId);
  }

  @Transactional
  public void delete(long memberId, long addressId) {
    ensureOwnership(memberId, addressId);
    jdbc.update("UPDATE members SET default_address_id=NULL WHERE id=? AND default_address_id=?", memberId, addressId);
    jdbc.update("DELETE FROM member_addresses WHERE id=?", addressId);
  }

  @Transactional
  public void makeDefault(long memberId, long addressId) {
    ensureOwnership(memberId, addressId);
    jdbc.update("UPDATE members SET default_address_id=? WHERE id=?", addressId, memberId);
    releaseAddressHolds(memberId);
  }

  @Transactional
  public void updateSubscriptionShipping(
      long memberId, long subscriptionId, AddressRequest request) {
    validate(request, false);
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscriptions WHERE id=? AND member_id=?",
            Integer.class,
            subscriptionId,
            memberId)
        != 1) {
      notFound("SUBSCRIPTION_NOT_FOUND");
    }
    int future =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM subscription_schedules schedule LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id
            WHERE schedule.subscription_id=? AND schedule.status IN ('SCHEDULED','HELD') AND context.order_id IS NULL\
            """,
            Integer.class,
            subscriptionId);
    if (future == 0) {
      throw new CommerceException(409, "SUBSCRIPTION_SHIPPING_NOT_CHANGEABLE", "변경 가능한 미래 Schedule이 없습니다.");
    }
    jdbc.update(
        """
        INSERT INTO subscription_shipping_snapshots(subscription_id,recipient_name,recipient_phone,postal_code,address_line1,address_line2,updated_at)
        VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE recipient_name=VALUES(recipient_name),recipient_phone=VALUES(recipient_phone),postal_code=VALUES(postal_code),address_line1=VALUES(address_line1),address_line2=VALUES(address_line2),updated_at=VALUES(updated_at)\
        """,
        subscriptionId,
        request.recipientName(),
        request.recipientPhone(),
        request.postalCode(),
        request.addressLine1(),
        request.addressLine2(),
        now());
    jdbc.update(
        """
        UPDATE subscription_schedules schedule LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id
        SET schedule.status='SCHEDULED',schedule.hold_reason=NULL WHERE schedule.subscription_id=? AND schedule.status='HELD'
        AND schedule.hold_reason='MISSING_SHIPPING_ADDRESS' AND context.order_id IS NULL\
        """,
        subscriptionId);
  }

  private void releaseAddressHolds(long memberId) {
    jdbc.update(
        "UPDATE subscription_schedules schedule JOIN subscriptions subscription ON"
            + " subscription.id=schedule.subscription_id JOIN members member ON"
            + " member.id=subscription.member_id LEFT JOIN subscription_shipping_snapshots snapshot"
            + " ON snapshot.subscription_id=subscription.id LEFT JOIN subscription_order_context"
            + " context ON context.schedule_id=schedule.id SET"
            + " schedule.status='SCHEDULED',schedule.hold_reason=NULL WHERE"
            + " subscription.member_id=? AND member.default_address_id IS NOT NULL AND"
            + " subscription.status='ACTIVE' AND snapshot.subscription_id IS NULL AND"
            + " schedule.status='HELD' AND schedule.hold_reason='MISSING_SHIPPING_ADDRESS' AND"
            + " context.order_id IS NULL",
        memberId);
  }

  private void ensureOwnership(long memberId, long addressId) {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM member_addresses WHERE id=? AND member_id=?",
            Integer.class,
            addressId,
            memberId)
        != 1) {
      notFound("ADDRESS_NOT_FOUND");
    }
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  private static void validate(AddressRequest request, boolean requireName) {
    if (requireName) validateRequiredLength(request.name(), "name", 100);
    validateRequiredLength(request.recipientName(), "recipientName", 100);
    validateRequiredLength(request.recipientPhone(), "recipientPhone", 30);
    validateRequiredLength(request.postalCode(), "postalCode", 20);
    validateRequiredLength(request.addressLine1(), "addressLine1", 255);
    if (request.addressLine2() != null && request.addressLine2().length() > 255) {
      throw new CommerceException(400, "VALIDATION_FAILED", "addressLine2 길이가 허용 범위를 초과했습니다.");
    }
  }

  private static void validateRequiredLength(String value, String key, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new CommerceException(400, "VALIDATION_FAILED", key + " is required");
    }
    if (value.length() > maxLength) {
      throw new CommerceException(400, "VALIDATION_FAILED", key + " 길이가 허용 범위를 초과했습니다.");
    }
  }

  private static void notFound(String code) {
    throw new CommerceException(404, code, "요청한 리소스를 찾을 수 없습니다.");
  }
}
