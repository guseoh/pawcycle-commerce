package com.pawcycle.backend.commerce;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CommerceService {
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transaction;
	private final TossPaymentAdapter tossPaymentAdapter;
	private final TossBillingAdapter tossBillingAdapter;
	private final DeliveryService deliveryService;
	private final NotificationService notificationService;
	private final InventoryService inventoryService;
	private final MembershipEvaluationService membershipEvaluation;
	private final int returnRequestDays;

	public CommerceService(
			JdbcTemplate jdbc,
			org.springframework.transaction.PlatformTransactionManager transactionManager,
			TossPaymentAdapter tossPaymentAdapter, TossBillingAdapter tossBillingAdapter, DeliveryService deliveryService, NotificationService notificationService,
			InventoryService inventoryService, MembershipEvaluationService membershipEvaluation,
			@org.springframework.beans.factory.annotation.Value("${pawcycle.commerce.return-request-days:7}") int returnRequestDays) {
		this.jdbc = jdbc;
		this.transaction = new TransactionTemplate(transactionManager);
		this.tossPaymentAdapter = tossPaymentAdapter;
		this.tossBillingAdapter = tossBillingAdapter;
		this.deliveryService = deliveryService;
		this.notificationService = notificationService;
		this.inventoryService = inventoryService;
		this.membershipEvaluation = membershipEvaluation;
		this.returnRequestDays = returnRequestDays;
	}

	public Map<String, Object> cart(long memberId) {
		List<Map<String, Object>> items = jdbc.queryForList("""
			SELECT item.sku_id AS skuId,item.quantity,sku.sku_code AS skuCode,sku.name AS skuName,sku.price,sku.price AS unitPrice,
			       sku.price * item.quantity AS lineAmount,product.id AS productId,product.name AS productName,
			       inventory.available_quantity AS availableQuantity,
			       (sku.status='ACTIVE' AND product.display_status='PUBLIC' AND inventory.available_quantity >= item.quantity) AS purchasable
			FROM carts cart JOIN cart_items item ON item.cart_id=cart.id
			JOIN skus sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id
			JOIN inventories inventory ON inventory.sku_id=sku.id
			WHERE cart.member_id=? ORDER BY item.sku_id""", memberId);
		BigDecimal original = items.stream()
				.map(item -> decimal(item, "lineAmount"))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return Map.of("items", items, "pricing", pricing(original, BigDecimal.ZERO, BigDecimal.ZERO, original));
	}

	public void addCartItem(long memberId, long skuId, int quantity) {
		transaction.executeWithoutResult(status -> {
			lockMember(memberId);
			requirePurchasableSku(skuId);
			Long cartId = jdbc.query(
					"SELECT id FROM carts WHERE member_id=? FOR UPDATE",
					rs -> rs.next() ? rs.getLong(1) : null,
					memberId);
			if (cartId == null) {
				jdbc.update("INSERT INTO carts(member_id,created_at,updated_at) VALUES (?,?,?)", memberId, now(), now());
				cartId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
			}
			int updated = jdbc.update(
					"UPDATE cart_items SET quantity=quantity+? WHERE cart_id=? AND sku_id=?",
					quantity, cartId, skuId);
			if (updated == 0) {
				jdbc.update("INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?,?,?)", cartId, skuId, quantity);
			}
			jdbc.update("UPDATE carts SET updated_at=? WHERE id=?", now(), cartId);
		});
	}

	public void updateCartItem(long memberId, long skuId, int quantity) {
		transaction.executeWithoutResult(status -> {
			Long cartId = jdbc.query(
					"SELECT id FROM carts WHERE member_id=? FOR UPDATE",
					rs -> rs.next() ? rs.getLong(1) : null,
					memberId);
			if (cartId == null
					|| jdbc.update("UPDATE cart_items SET quantity=? WHERE cart_id=? AND sku_id=?", quantity, cartId, skuId) != 1) {
				notFound("CART_ITEM_NOT_FOUND");
			}
		});
	}

	public void deleteCartItem(long memberId, long skuId) {
		transaction.executeWithoutResult(status -> jdbc.update(
				"DELETE item FROM cart_items item JOIN carts cart ON cart.id=item.cart_id WHERE cart.member_id=? AND item.sku_id=?",
				memberId, skuId));
	}

	public Map<String, Object> wishlist(long memberId) {
		return Map.of("items", jdbc.queryForList("""
			SELECT item.product_id AS productId,product.name AS productName,item.created_at AS createdAt
			FROM wishlist_items item JOIN products product ON product.id=item.product_id
			WHERE item.member_id=? ORDER BY item.created_at DESC,item.product_id DESC""", memberId));
	}

	public void addWishlist(long memberId, long productId) {
		if (jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?", Integer.class, productId) == 0) {
			notFound("PRODUCT_NOT_FOUND");
		}
		try {
			jdbc.update("INSERT INTO wishlist_items(member_id,product_id,created_at) VALUES (?,?,?)", memberId, productId, now());
		} catch (org.springframework.dao.DuplicateKeyException ignored) {
			// Wishlist add is idempotent.
		}
	}

	public void deleteWishlist(long memberId, long productId) {
		jdbc.update("DELETE FROM wishlist_items WHERE member_id=? AND product_id=?", memberId, productId);
	}

	public List<Map<String, Object>> addresses(long memberId) {
		return jdbc.queryForList("""
			SELECT address.id AS addressId,address.name,address.recipient_name AS recipientName,address.recipient_phone AS recipientPhone,
			address.postal_code AS postalCode,address.address_line1 AS addressLine1,address.address_line2 AS addressLine2,
			(member.default_address_id=address.id) AS isDefault
			FROM member_addresses address JOIN members member ON member.id=address.member_id
			WHERE address.member_id=? ORDER BY address.id""", memberId);
	}

	public long createAddress(long memberId, Map<String, Object> request) {
		validateAddressPayload(request, true);
		return transaction.execute(status -> {
			jdbc.update(
					"INSERT INTO member_addresses(member_id,name,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
					memberId,
					required(request, "name"),
					required(request, "recipientName"),
					required(request, "recipientPhone"),
					required(request, "postalCode"),
					required(request, "addressLine1"),
					nullable(request, "addressLine2"),
					now(), now());
			long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
			Integer defaults = jdbc.queryForObject(
					"SELECT COUNT(*) FROM members WHERE id=? AND default_address_id IS NOT NULL",
					Integer.class,
					memberId);
			if (defaults == 0) jdbc.update("UPDATE members SET default_address_id=? WHERE id=?", id, memberId);
			releaseAddressHolds(memberId);
			return id;
		});
	}

	public void updateAddress(long memberId, long addressId, Map<String, Object> request) {
		validateAddressPayload(request, true);
		transaction.executeWithoutResult(status -> {
			ensureAddressOwnership(memberId, addressId);
			jdbc.update(
					"UPDATE member_addresses SET name=?,recipient_name=?,recipient_phone=?,postal_code=?,address_line1=?,address_line2=?,updated_at=? WHERE id=?",
					required(request, "name"),
					required(request, "recipientName"),
					required(request, "recipientPhone"),
					required(request, "postalCode"),
					required(request, "addressLine1"),
					nullable(request, "addressLine2"),
					now(), addressId);
			releaseAddressHolds(memberId);
		});
	}

	public void deleteAddress(long memberId, long addressId) {
		transaction.executeWithoutResult(status -> {
			ensureAddressOwnership(memberId, addressId);
			jdbc.update("UPDATE members SET default_address_id=NULL WHERE id=? AND default_address_id=?", memberId, addressId);
			jdbc.update("DELETE FROM member_addresses WHERE id=?", addressId);
		});
	}

	public void defaultAddress(long memberId, long addressId) {
		transaction.executeWithoutResult(status -> {
			ensureAddressOwnership(memberId, addressId);
			jdbc.update("UPDATE members SET default_address_id=? WHERE id=?", addressId, memberId);
			releaseAddressHolds(memberId);
		});
	}

	public void updateSubscriptionShipping(long memberId, long subscriptionId, Map<String,Object> request) {
		validateAddressPayload(request, false);
		transaction.executeWithoutResult(status -> {
			if (jdbc.queryForObject(
					"SELECT COUNT(*) FROM subscriptions WHERE id=? AND member_id=?",
					Integer.class,
					subscriptionId, memberId) != 1) {
				notFound("SUBSCRIPTION_NOT_FOUND");
			}
			int future = jdbc.queryForObject("""
				SELECT COUNT(*) FROM subscription_schedules schedule LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id
				WHERE schedule.subscription_id=? AND schedule.status IN ('SCHEDULED','HELD') AND context.order_id IS NULL""", Integer.class, subscriptionId);
			if (future == 0) {
				throw new CommerceException(409,"SUBSCRIPTION_SHIPPING_NOT_CHANGEABLE","변경 가능한 미래 Schedule이 없습니다.");
			}
			jdbc.update("""
				INSERT INTO subscription_shipping_snapshots(subscription_id,recipient_name,recipient_phone,postal_code,address_line1,address_line2,updated_at)
				VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE recipient_name=VALUES(recipient_name),recipient_phone=VALUES(recipient_phone),postal_code=VALUES(postal_code),address_line1=VALUES(address_line1),address_line2=VALUES(address_line2),updated_at=VALUES(updated_at)""",
				subscriptionId,
				required(request,"recipientName"), required(request,"recipientPhone"), required(request,"postalCode"),
				required(request,"addressLine1"), nullable(request,"addressLine2"), now());
			jdbc.update("""
				UPDATE subscription_schedules schedule LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id
				SET schedule.status='SCHEDULED',schedule.hold_reason=NULL WHERE schedule.subscription_id=? AND schedule.status='HELD'
				AND schedule.hold_reason='MISSING_SHIPPING_ADDRESS' AND context.order_id IS NULL""", subscriptionId);
		});
	}

	public Map<String,Object> checkout(long memberId, String idempotencyKey, long addressId, Long memberCouponId) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
			throw new CommerceException(400,"IDEMPOTENCY_KEY_REQUIRED","Idempotency-Key가 필요합니다.");
		}
		return transaction.execute(status -> {
			lockMember(memberId);
			Map<String,Object> replay = one("""
				SELECT orders.id AS orderId,orders.order_number AS orderNumber,payment.id AS paymentId,payment.provider_order_id AS providerOrderId,orders.payment_amount AS amount
				FROM checkout_idempotency_results result JOIN orders ON orders.id=result.order_id JOIN payments payment ON payment.id=result.payment_id
				WHERE result.member_id=? AND result.idempotency_key=?""", memberId, idempotencyKey);
			if (replay != null) return checkoutResponse(replay);

			Map<String,Object> address = one(
					"SELECT id,recipient_name,recipient_phone,postal_code,address_line1,address_line2 FROM member_addresses WHERE id=? AND member_id=?",
					addressId, memberId);
			if (address == null) notFound("ADDRESS_NOT_FOUND");

			List<Map<String,Object>> items = jdbc.queryForList("""
				SELECT item.sku_id,item.quantity,sku.sku_code,sku.name AS sku_name,sku.price,product.name AS product_name
				FROM carts cart JOIN cart_items item ON item.cart_id=cart.id JOIN skus sku ON sku.id=item.sku_id
				JOIN products product ON product.id=sku.product_id WHERE cart.member_id=? FOR UPDATE""", memberId);
			if (items.isEmpty()) throw new CommerceException(409,"CART_EMPTY","장바구니가 비어 있습니다.");

			BigDecimal original = BigDecimal.ZERO;
			for (Map<String,Object> item : items) {
				requirePurchasableSku(number(item,"sku_id"));
				original = original.add(decimal(item,"price").multiply(BigDecimal.valueOf(number(item,"quantity"))));
			}
			BigDecimal discount = memberCouponId == null ? BigDecimal.ZERO : reserveCoupon(memberId, memberCouponId, original);
			BigDecimal amount = original.subtract(discount);
			if (amount.compareTo(BigDecimal.valueOf(100)) < 0) {
				throw new CommerceException(409,"PAYMENT_AMOUNT_TOO_LOW","결제 금액은 100원 이상이어야 합니다.");
			}

			String orderNumber = "O-" + UUID.randomUUID();
			jdbc.update("INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at) VALUES (?,?,'ONE_TIME','PAYMENT_PENDING',?,?,?,?,?,?,?,?,?,?)",
					orderNumber, memberId, original, discount, BigDecimal.ZERO, amount,
					address.get("recipient_name"), address.get("recipient_phone"), address.get("postal_code"),
					address.get("address_line1"), address.get("address_line2"), now());
			long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

			String providerOrderId = "TOSS-" + UUID.randomUUID();
			String paymentIdempotency = "pay-" + UUID.randomUUID();
			Timestamp expiresAt = Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES));
			jdbc.update("INSERT INTO payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,attempt_no,requested_at,expires_at,created_at) VALUES (?,'NORMAL','TOSS','READY',?,?,?,?,?,?,?)",
					orderId, amount, providerOrderId, paymentIdempotency, 1, now(), expiresAt, now());
			long paymentId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

			for (Map<String,Object> item : items) {
				long skuId = number(item,"sku_id");
				int quantity = (int) number(item,"quantity");
				inventoryService.reserve(skuId, quantity, paymentId);
				BigDecimal price = decimal(item,"price");
				jdbc.update("INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount) VALUES (?,?,'FULL',?,?,?,?,?,?)",
						orderId, skuId, item.get("sku_code"), item.get("product_name"), item.get("sku_name"),
						price, quantity, price.multiply(BigDecimal.valueOf(quantity)));
			}
			if (memberCouponId != null) {
				jdbc.update("UPDATE member_coupons SET status='RESERVED',reserved_order_id=? WHERE id=? AND member_id=? AND status='AVAILABLE'",
						orderId, memberCouponId, memberId);
			}
			jdbc.update("INSERT INTO checkout_idempotency_results(member_id,idempotency_key,order_id,payment_id,created_at) VALUES (?,?,?,?,?)",
					memberId, idempotencyKey, orderId, paymentId, now());
			Map<String, Object> result = new LinkedHashMap<>(Map.of(
					"orderId", orderId,
					"orderNumber", orderNumber,
					"paymentId", paymentId,
					"providerOrderId", providerOrderId,
					"orderName", orderName(items),
					"amount", amount));
			result.put("pricing", pricing(original, discount, BigDecimal.ZERO, amount));
			return result;
		});
	}

	public Map<String,Object> confirm(long memberId, String paymentKey, String providerOrderId, BigDecimal amount) {
		if (!tossPaymentAdapter.isConfigured()) {
			throw new CommerceException(503,"PAYMENT_PROVIDER_UNAVAILABLE","Toss 결제 Provider가 현재 환경에 구성되지 않았습니다.");
		}
		Map<String,Object> work = transaction.execute(status -> {
			Map<String,Object> payment = one("""
				SELECT payment.id,payment.order_id,payment.amount,payment.status,payment.payment_key,orders.member_id,orders.status AS order_status
				FROM payments payment JOIN orders ON orders.id=payment.order_id WHERE payment.provider_order_id=? FOR UPDATE""", providerOrderId);
			if (payment == null) notFound("PAYMENT_NOT_FOUND");
			if (number(payment,"member_id") != memberId) {
				throw new CommerceException(403,"PAYMENT_FORBIDDEN","결제 소유자가 아닙니다.");
			}
			if ("SUCCEEDED".equals(payment.get("status"))
					&& "PAID".equals(payment.get("order_status"))
					&& decimal(payment,"amount").compareTo(amount) == 0
					&& paymentKey.equals(payment.get("payment_key"))) {
				payment.put("replay", true);
				return payment;
			}
			if (!"READY".equals(payment.get("status"))
					|| !"PAYMENT_PENDING".equals(payment.get("order_status"))
					|| decimal(payment,"amount").compareTo(amount) != 0) {
				throw new CommerceException(409,"PAYMENT_CONFIRM_CONFLICT","결제 확인 상태가 올바르지 않습니다.");
			}
			jdbc.update("UPDATE payments SET status='PROCESSING',payment_key=?,provider_status='REQUESTED' WHERE id=?",
					paymentKey, number(payment,"id"));
			return payment;
		});

		if (Boolean.TRUE.equals(work.get("replay"))) {
			return Map.of("paymentId", number(work,"id"), "orderId", number(work,"order_id"), "status", "SUCCEEDED");
		}

		try {
			TossPaymentAdapter.ConfirmResult result = tossPaymentAdapter.confirm(paymentKey, providerOrderId, amount);
			return transaction.execute(status -> finalizePayment(number(work,"id"), result.status(), paymentKey));
		} catch (RuntimeException exception) {
			return transaction.execute(status -> markProviderUnknown(number(work,"id")));
		}
	}

	public List<Map<String,Object>> orders(long memberId) {
		return jdbc.queryForList(
				"SELECT id AS orderId,order_number AS orderNumber,source,status,payment_amount AS paymentAmount,created_at AS createdAt,paid_at AS paidAt FROM orders WHERE member_id=? ORDER BY id DESC",
				memberId);
	}

	public Map<String,Object> order(long memberId,long orderId) {
		Map<String,Object> order = one(
				"SELECT id AS orderId,order_number AS orderNumber,source,status,original_amount AS originalAmount,discount_amount AS discountAmount,shipping_fee AS shippingFee,payment_amount AS paymentAmount,recipient_name AS recipientName,recipient_phone AS recipientPhone,postal_code AS postalCode,address_line1 AS addressLine1,address_line2 AS addressLine2,created_at AS createdAt,paid_at AS paidAt FROM orders WHERE id=? AND member_id=?",
				orderId, memberId);
		if (order == null) notFound("ORDER_NOT_FOUND");
		order.put("items", jdbc.queryForList(
				"SELECT sku_id AS skuId,snapshot_quality AS snapshotQuality,sku_code_snapshot AS skuCodeSnapshot,product_name_snapshot AS productNameSnapshot,sku_name_snapshot AS skuNameSnapshot,unit_price AS unitPrice,quantity,line_amount AS lineAmount FROM order_items WHERE order_id=? ORDER BY id",
				orderId));
		order.put("payment", one("SELECT id AS paymentId,type,provider,status,amount,attempt_no AS attemptNo,provider_status AS providerStatus FROM payments WHERE order_id=? ORDER BY attempt_no DESC LIMIT 1",orderId));
		order.put("delivery", one("SELECT id AS deliveryId,status,carrier_code AS carrierCode,tracking_number AS trackingNumber,failure_reason AS failureReason,shipped_at AS shippedAt,delivered_at AS deliveredAt FROM deliveries WHERE order_id=?",orderId));
		order.put("cancellation", one("SELECT id AS cancellationId,status,reason,requested_at AS requestedAt,completed_at AS completedAt FROM order_cancellations WHERE order_id=?",orderId));
		order.put("return", one("SELECT id AS returnId,status,reason,rejection_reason AS rejectionReason,restock,requested_at AS requestedAt,received_at AS receivedAt,completed_at AS completedAt FROM order_returns WHERE order_id=?",orderId));
		order.put("refunds", jdbc.queryForList("SELECT id AS refundId,source,status,amount,attempt_no AS attemptNo,reconciliation_attempts AS reconciliationAttempts FROM refunds WHERE order_id=? ORDER BY attempt_no",orderId));
		Map<String,Object> delivery=(Map<String,Object>)order.get("delivery");
		List<String> actions=new java.util.ArrayList<>();
		if ("PAID".equals(order.get("status")) && delivery != null && "PREPARING".equals(delivery.get("status")) && order.get("cancellation")==null) actions.add("REQUEST_CANCELLATION");
		Timestamp deliveredAt = delivery == null ? null : (Timestamp) delivery.get("deliveredAt");
		boolean returnWindowOpen = deliveredAt != null
				&& !deliveredAt.toInstant().plus(returnRequestDays, ChronoUnit.DAYS).isBefore(Instant.now());
		if (delivery != null && "DELIVERED".equals(delivery.get("status")) && returnWindowOpen && order.get("return")==null) actions.add("REQUEST_RETURN");
		order.put("availableActions",actions);
		return order;
	}

	public Map<String,Object> prepareBilling(long memberId) {
		return transaction.execute(status -> {
			String token = "bp-" + UUID.randomUUID();
			jdbc.update("INSERT INTO billing_payment_method_preparations(prepare_token,member_id,customer_key,expires_at) VALUES (?,?,?,?)",
					token, memberId, "cust-" + UUID.randomUUID(), Timestamp.from(Instant.now().plus(10,ChronoUnit.MINUTES)));
			return Map.of("prepareToken", token);
		});
	}

	public void completeBilling(long memberId,String prepareToken,String authKey) {
		if (!tossBillingAdapter.isConfigured()) {
			throw new CommerceException(503,"PAYMENT_PROVIDER_UNAVAILABLE","Toss Billing Provider가 현재 환경에 구성되지 않았습니다.");
		}
		if (authKey == null || authKey.isBlank()) {
			throw new CommerceException(400,"VALIDATION_FAILED","authKey가 필요합니다.");
		}
		Map<String,Object> prepared = transaction.execute(status -> {
			int claimed = jdbc.update("UPDATE billing_payment_method_preparations SET status='PROCESSING',claimed_at=? WHERE prepare_token=? AND member_id=? AND status='READY' AND expires_at>?", now(), prepareToken, memberId, now());
			if (claimed != 1) {
				Map<String,Object> current = one("SELECT status,expires_at FROM billing_payment_method_preparations WHERE prepare_token=? AND member_id=?", prepareToken, memberId);
				if (current != null && "PROCESSING".equals(current.get("status"))) {
					throw new CommerceException(409,"BILLING_PREPARATION_IN_PROGRESS","동일 Billing 준비 요청이 이미 Provider 처리 중입니다.");
				}
				throw new CommerceException(409,"BILLING_PREPARATION_INVALID","Billing 준비 정보가 유효하지 않습니다.");
			}
			return one("SELECT customer_key FROM billing_payment_method_preparations WHERE prepare_token=? AND member_id=? AND status='PROCESSING'", prepareToken, memberId);
		});
		// Provider I/O is deliberately outside the persistence transaction.
		String billingKey = tossBillingAdapter.issueBillingKey((String) prepared.get("customer_key"), authKey).billingKey();
		transaction.executeWithoutResult(status -> {
			Map<String,Object> prep = one(
					"SELECT customer_key,status FROM billing_payment_method_preparations WHERE prepare_token=? AND member_id=? AND status='PROCESSING' FOR UPDATE",
					prepareToken, memberId);
			if (prep == null) {
				throw new CommerceException(409,"BILLING_PREPARATION_INVALID","Billing 준비 정보가 유효하지 않습니다.");
			}
			jdbc.update("UPDATE billing_payment_methods SET status='REVOKED',revoked_at=? WHERE member_id=? AND status='ACTIVE'", now(), memberId);
			jdbc.update("INSERT INTO billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at) VALUES (?,'TOSS',?,?,'ACTIVE',?)",
					memberId, prep.get("customer_key"), billingKey, now());
			jdbc.update("DELETE FROM billing_payment_method_preparations WHERE prepare_token=?", prepareToken);
			jdbc.update("UPDATE subscription_schedules schedule JOIN subscriptions subscription ON subscription.id=schedule.subscription_id LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id SET schedule.status='SCHEDULED',schedule.hold_reason=NULL WHERE subscription.member_id=? AND subscription.status='ACTIVE' AND schedule.status='HELD' AND schedule.hold_reason='MISSING_BILLING_METHOD' AND context.order_id IS NULL",
					memberId);
		});
	}

	public List<Map<String,Object>> inventories() {
		return jdbc.queryForList("SELECT inventory.sku_id AS skuId,inventory.available_quantity AS availableQuantity,inventory.reserved_quantity AS reservedQuantity,inventory.version,sku.sku_code AS skuCode FROM inventories inventory JOIN skus sku ON sku.id=inventory.sku_id ORDER BY inventory.sku_id");
	}

	public void adjustInventory(long skuId,int delta) {
		if (delta == 0) {
			throw new CommerceException(400,"INVENTORY_ADJUSTMENT_INVALID","재고 조정 수량은 0일 수 없습니다.");
		}
		transaction.executeWithoutResult(status -> inventoryService.adjust(skuId, delta));
	}

	public List<Map<String,Object>> memberCoupons(long memberId) {
		return jdbc.queryForList("SELECT member_coupon.id AS memberCouponId,coupon.id AS couponId,coupon.name,coupon.discount_type AS discountType,coupon.discount_value AS discountValue,member_coupon.status,coupon.valid_from AS validFrom,coupon.valid_until AS validUntil FROM member_coupons member_coupon JOIN coupons coupon ON coupon.id=member_coupon.coupon_id WHERE member_coupon.member_id=? ORDER BY member_coupon.id DESC", memberId);
	}

	public List<Map<String,Object>> coupons() {
		return jdbc.queryForList("SELECT id AS couponId,name,discount_type AS discountType,discount_value AS discountValue,minimum_order_amount AS minimumOrderAmount,maximum_discount_amount AS maximumDiscountAmount,valid_from AS validFrom,valid_until AS validUntil,active FROM coupons ORDER BY id");
	}

	public long createCoupon(Map<String,Object> request) {
		return transaction.execute(status -> {
			jdbc.update("INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,maximum_discount_amount,valid_from,valid_until,active) VALUES (?,?,?,?,?,?,?,?)",
					required(request,"name"), required(request,"discountType"), new BigDecimal(required(request,"discountValue")),
					new BigDecimal(required(request,"minimumOrderAmount")),
					nullable(request,"maximumDiscountAmount") == null ? null : new BigDecimal(nullable(request,"maximumDiscountAmount")),
					Timestamp.valueOf(required(request,"validFrom")), Timestamp.valueOf(required(request,"validUntil")),
					Boolean.parseBoolean(required(request,"active")));
			return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		});
	}

	public void updateCoupon(long couponId,Map<String,Object> request) {
		transaction.executeWithoutResult(status -> {
			if (jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE id=?", Integer.class, couponId) == 0) notFound("COUPON_NOT_FOUND");
			jdbc.update("UPDATE coupons SET name=?,discount_type=?,discount_value=?,minimum_order_amount=?,maximum_discount_amount=?,valid_from=?,valid_until=?,active=? WHERE id=?",
					required(request,"name"), required(request,"discountType"), new BigDecimal(required(request,"discountValue")),
					new BigDecimal(required(request,"minimumOrderAmount")),
					nullable(request,"maximumDiscountAmount") == null ? null : new BigDecimal(nullable(request,"maximumDiscountAmount")),
					Timestamp.valueOf(required(request,"validFrom")), Timestamp.valueOf(required(request,"validUntil")),
					Boolean.parseBoolean(required(request,"active")), couponId);
		});
	}

	public void issueCoupon(long couponId,long memberId) {
		transaction.executeWithoutResult(status -> {
			if (jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE id=?", Integer.class, couponId) == 0) notFound("COUPON_NOT_FOUND");
			if (jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE id=?", Integer.class, memberId) == 0) notFound("MEMBER_NOT_FOUND");
			jdbc.update("INSERT INTO member_coupons(member_id,coupon_id,status,issued_at) VALUES (?,?,'AVAILABLE',?)", memberId, couponId, now());
		});
	}

	public Map<String,Object> membership(long memberId) {
		Map<String,Object> result = one("SELECT grade.code,grade.name,membership.evaluated_purchase_amount AS evaluatedPurchaseAmount,membership.evaluated_at AS evaluatedAt FROM member_memberships membership JOIN membership_grades grade ON grade.id=membership.grade_id WHERE membership.member_id=?", memberId);
		if (result != null) return result;
		return one("SELECT code,name,0 AS evaluatedPurchaseAmount,NULL AS evaluatedAt FROM membership_grades WHERE code='BASIC'");
	}

	public List<Map<String,Object>> membershipGrades() {
		return jdbc.queryForList("SELECT id AS gradeId,code,name,minimum_purchase_amount AS minimumPurchaseAmount,display_order AS displayOrder,active,benefit_coupon_id AS benefitCouponId FROM membership_grades ORDER BY display_order,id");
	}

	public long createMembershipGrade(Map<String,Object> request) {
		return transaction.execute(status -> {
			jdbc.update("INSERT INTO membership_grades(code,name,minimum_purchase_amount,display_order,active,benefit_coupon_id) VALUES (?,?,?,?,?,?)",
					required(request,"code"), required(request,"name"), new BigDecimal(required(request,"minimumPurchaseAmount")),
					Integer.parseInt(required(request,"displayOrder")), Boolean.parseBoolean(required(request,"active")), nullable(request,"benefitCouponId"));
			return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		});
	}

	public void evaluateMembership(long memberId) {
		membershipEvaluation.evaluate(memberId);
	}

	private Map<String,Object> finalizePayment(long paymentId,String result,String paymentKey) {
		Map<String,Object> payment = one("SELECT id,order_id,status FROM payments WHERE id=? FOR UPDATE", paymentId);
		if (payment == null) notFound("PAYMENT_NOT_FOUND");
		if (!"PROCESSING".equals(payment.get("status"))) {
			return Map.of("paymentId", paymentId, "orderId", payment.get("order_id"), "status", payment.get("status"));
		}
		long orderId = number(payment,"order_id");
		if ("UNKNOWN".equals(result)) {
			jdbc.update("UPDATE payments SET status='UNKNOWN',provider_status='UNKNOWN' WHERE id=?", paymentId);
			return Map.of("paymentId", paymentId, "orderId", orderId, "status", "UNKNOWN");
		}
		List<Map<String,Object>> items = jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=?", orderId);
		if ("SUCCEEDED".equals(result)) {
			for (Map<String,Object> item : items) {
				inventoryService.deduct(number(item,"sku_id"), (int) number(item,"quantity"), paymentId);
			}
			jdbc.update("UPDATE payments SET status='SUCCEEDED',provider_status='DONE',payment_key=?,approved_at=? WHERE id=?", paymentKey, now(), paymentId);
			jdbc.update("UPDATE orders SET status='PAID',paid_at=? WHERE id=?", now(), orderId);
			deliveryService.createPreparing(orderId);
			jdbc.update("UPDATE member_coupons SET status='USED',used_at=? WHERE reserved_order_id=? AND status='RESERVED'", now(), orderId);
			long memberId = jdbc.queryForObject("SELECT member_id FROM orders WHERE id=?", Long.class, orderId);
			notificationService.create(memberId,"ORDER_PAID","ORDER",orderId);
			consumeCartForOrder(memberId, orderId);
			membershipEvaluation.evaluate(memberId);
			return Map.of("paymentId", paymentId, "orderId", orderId, "status", "SUCCEEDED");
		}
		for (Map<String,Object> item : items) {
			inventoryService.release(number(item,"sku_id"), (int) number(item,"quantity"), paymentId);
		}
		jdbc.update("UPDATE payments SET status='FAILED',provider_status='ABORTED',failure_code='TOSS_REJECTED',failed_at=? WHERE id=?", now(), paymentId);
		jdbc.update("UPDATE orders SET status='PAYMENT_FAILED' WHERE id=?", orderId);
		jdbc.update("UPDATE member_coupons SET status='AVAILABLE',reserved_order_id=NULL WHERE reserved_order_id=? AND status='RESERVED'", orderId);
		return Map.of("paymentId", paymentId, "orderId", orderId, "status", "FAILED");
	}

	private Map<String,Object> markProviderUnknown(long paymentId) {
		Map<String,Object> payment = one("SELECT order_id,status FROM payments WHERE id=? FOR UPDATE", paymentId);
		if (payment == null) notFound("PAYMENT_NOT_FOUND");
		if ("PROCESSING".equals(payment.get("status"))) {
			jdbc.update("UPDATE payments SET status='UNKNOWN',provider_status='UNKNOWN',failure_code='PROVIDER_RESULT_UNKNOWN' WHERE id=?", paymentId);
		}
		return Map.of("paymentId", paymentId, "orderId", payment.get("order_id"), "status", "UNKNOWN");
	}

	private void reserveInventory(long skuId,int quantity,long paymentId) {
		Map<String,Object> inventory = one("SELECT available_quantity,reserved_quantity,version FROM inventories WHERE sku_id=?", skuId);
		if (inventory == null || number(inventory,"available_quantity") < quantity) {
			throw new CommerceException(409,"INVENTORY_INSUFFICIENT","재고가 부족합니다.");
		}
		int changed = jdbc.update("UPDATE inventories SET available_quantity=available_quantity-?,reserved_quantity=reserved_quantity+?,version=version+1 WHERE sku_id=? AND version=? AND available_quantity>=?",
				quantity, quantity, skuId, number(inventory,"version"), quantity);
		if (changed != 1) throw new CommerceException(409,"INVENTORY_CONFLICT","재고가 변경되었습니다.");
		jdbc.update("INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at) VALUES (?,?,'RESERVE',?,?,?,?,?,?)",
				skuId, paymentId, quantity,
				number(inventory,"available_quantity"), number(inventory,"available_quantity") - quantity,
				number(inventory,"reserved_quantity"), number(inventory,"reserved_quantity") + quantity, now());
	}

	private void releaseInventory(long skuId,int quantity,long paymentId) {
		Map<String,Object> inventory = one("SELECT available_quantity,reserved_quantity FROM inventories WHERE sku_id=? FOR UPDATE", skuId);
		jdbc.update("UPDATE inventories SET available_quantity=available_quantity+?,reserved_quantity=reserved_quantity-?,version=version+1 WHERE sku_id=?",
				quantity, quantity, skuId);
		jdbc.update("INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at) VALUES (?,?,'RELEASE',?,?,?,?,?,?)",
				skuId, paymentId, quantity,
				number(inventory,"available_quantity"), number(inventory,"available_quantity") + quantity,
				number(inventory,"reserved_quantity"), number(inventory,"reserved_quantity") - quantity, now());
	}

	private void deductInventory(long skuId,int quantity,long paymentId) {
		Map<String,Object> inventory = one("SELECT available_quantity,reserved_quantity FROM inventories WHERE sku_id=? FOR UPDATE", skuId);
		jdbc.update("UPDATE inventories SET reserved_quantity=reserved_quantity-?,version=version+1 WHERE sku_id=?", quantity, skuId);
		jdbc.update("INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at) VALUES (?,?,'DEDUCT',?,?,?,?,?,?)",
				skuId, paymentId, quantity,
				number(inventory,"available_quantity"), number(inventory,"available_quantity"),
				number(inventory,"reserved_quantity"), number(inventory,"reserved_quantity") - quantity, now());
	}

	private BigDecimal reserveCoupon(long memberId,long id,BigDecimal original) {
		Map<String,Object> coupon = one("SELECT coupon.discount_type,coupon.discount_value,coupon.minimum_order_amount,coupon.maximum_discount_amount FROM member_coupons member_coupon JOIN coupons coupon ON coupon.id=member_coupon.coupon_id WHERE member_coupon.id=? AND member_coupon.member_id=? AND member_coupon.status='AVAILABLE' AND coupon.active=true AND coupon.valid_from<=? AND coupon.valid_until>? FOR UPDATE",
				id, memberId, now(), now());
		if (coupon == null) throw new CommerceException(409,"COUPON_UNAVAILABLE","사용할 수 없는 쿠폰입니다.");
		if (original.compareTo(decimal(coupon,"minimum_order_amount")) < 0) {
			throw new CommerceException(409,"COUPON_MINIMUM_ORDER","최소 주문 금액을 충족하지 않습니다.");
		}
		BigDecimal discount = "PERCENTAGE".equals(coupon.get("discount_type"))
				? original.multiply(decimal(coupon,"discount_value")).divide(BigDecimal.valueOf(100),0,RoundingMode.DOWN)
				: decimal(coupon,"discount_value");
		if (coupon.get("maximum_discount_amount") != null) discount = discount.min(decimal(coupon,"maximum_discount_amount"));
		return discount.min(original);
	}

	private void consumeCartForOrder(long memberId, long orderId) {
		Long cartId = jdbc.query(
				"SELECT id FROM carts WHERE member_id=? FOR UPDATE",
				rs -> rs.next() ? rs.getLong(1) : null,
				memberId);
		if (cartId == null) return;
		for (Map<String,Object> item : jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=?", orderId)) {
			long skuId = number(item,"sku_id");
			int purchased = (int) number(item,"quantity");
			Integer current = jdbc.query(
					"SELECT quantity FROM cart_items WHERE cart_id=? AND sku_id=? FOR UPDATE",
					rs -> rs.next() ? rs.getInt(1) : null,
					cartId, skuId);
			if (current == null) continue;
			if (current <= purchased) {
				jdbc.update("DELETE FROM cart_items WHERE cart_id=? AND sku_id=?", cartId, skuId);
			} else {
				jdbc.update("UPDATE cart_items SET quantity=? WHERE cart_id=? AND sku_id=?", current - purchased, cartId, skuId);
			}
		}
		jdbc.update("UPDATE carts SET updated_at=? WHERE id=?", now(), cartId);
	}

	private void releaseAddressHolds(long memberId) {
		jdbc.update("UPDATE subscription_schedules schedule JOIN subscriptions subscription ON subscription.id=schedule.subscription_id JOIN members member ON member.id=subscription.member_id LEFT JOIN subscription_shipping_snapshots snapshot ON snapshot.subscription_id=subscription.id LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id SET schedule.status='SCHEDULED',schedule.hold_reason=NULL WHERE subscription.member_id=? AND member.default_address_id IS NOT NULL AND subscription.status='ACTIVE' AND snapshot.subscription_id IS NULL AND schedule.status='HELD' AND schedule.hold_reason='MISSING_SHIPPING_ADDRESS' AND context.order_id IS NULL",
				memberId);
	}

	private void lockMember(long memberId) {
		Long locked = jdbc.query("SELECT id FROM members WHERE id=? FOR UPDATE", rs -> rs.next() ? rs.getLong(1) : null, memberId);
		if (locked == null) notFound("MEMBER_NOT_FOUND");
	}

	private void ensureAddressOwnership(long memberId,long addressId) {
		if (jdbc.queryForObject("SELECT COUNT(*) FROM member_addresses WHERE id=? AND member_id=?", Integer.class, addressId, memberId) != 1) {
			notFound("ADDRESS_NOT_FOUND");
		}
	}

	private void requirePurchasableSku(long skuId) {
		if (jdbc.queryForObject("SELECT COUNT(*) FROM skus sku JOIN products product ON product.id=sku.product_id WHERE sku.id=? AND sku.status='ACTIVE' AND product.display_status='PUBLIC'", Integer.class, skuId) != 1) {
			throw new CommerceException(409,"SKU_NOT_PURCHASABLE","구매할 수 없는 SKU입니다.");
		}
	}

	private static void validateAddressPayload(Map<String,Object> request, boolean requireName) {
		if (requireName) validateRequiredLength(request, "name", 100);
		validateRequiredLength(request, "recipientName", 100);
		validateRequiredLength(request, "recipientPhone", 30);
		validateRequiredLength(request, "postalCode", 20);
		validateRequiredLength(request, "addressLine1", 255);
		String addressLine2 = nullable(request, "addressLine2");
		if (addressLine2 != null && addressLine2.length() > 255) {
			throw new CommerceException(400,"VALIDATION_FAILED","addressLine2 길이가 허용 범위를 초과했습니다.");
		}
	}

	private static void validateRequiredLength(Map<String,Object> request, String key, int maxLength) {
		String value = required(request, key);
		if (value.length() > maxLength) {
			throw new CommerceException(400,"VALIDATION_FAILED",key + " 길이가 허용 범위를 초과했습니다.");
		}
	}

	private Map<String,Object> one(String sql,Object... args) {
		List<Map<String,Object>> rows = jdbc.queryForList(sql,args);
		return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
	}

	private static long number(Map<String,Object> row,String key) {
		return ((Number)row.get(key)).longValue();
	}

	private static BigDecimal decimal(Map<String,Object> row,String key) {
		Object value = row.get(key);
		return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
	}

	private static String required(Map<String,Object> request,String key) {
		String value = nullable(request,key);
		if (value == null || value.isBlank()) {
			throw new CommerceException(400,"VALIDATION_FAILED",key + " is required");
		}
		return value;
	}

	private static String nullable(Map<String,Object> request,String key) {
		Object value = request.get(key);
		return value == null ? null : value.toString();
	}

	private static Timestamp now() {
		return Timestamp.from(Instant.now());
	}

	private static void notFound(String code) {
		throw new CommerceException(404,code,"요청한 리소스를 찾을 수 없습니다.");
	}

	private static String orderName(List<Map<String,Object>> items) {
		return items.getFirst().get("product_name") + (items.size() > 1 ? " 외 " + (items.size() - 1) + "건" : "");
	}

	private Map<String,Object> checkoutResponse(Map<String,Object> row) {
		long orderId = number(row,"orderId");
		List<Map<String,Object>> items = jdbc.queryForList(
				"SELECT product_name_snapshot AS product_name FROM order_items WHERE order_id=? ORDER BY id",
				orderId);
		Map<String, Object> result = new LinkedHashMap<>(Map.of(
				"orderId", row.get("orderId"),
				"orderNumber", row.get("orderNumber"),
				"paymentId", row.get("paymentId"),
				"providerOrderId", row.get("providerOrderId"),
				"orderName", orderName(items),
				"amount", row.get("amount")));
		Map<String, Object> order = one("SELECT original_amount AS originalAmount,discount_amount AS discountAmount,shipping_fee AS shippingFee,payment_amount AS paymentAmount FROM orders WHERE id=?", orderId);
		result.put("pricing", pricing(decimal(order, "originalAmount"), decimal(order, "discountAmount"), decimal(order, "shippingFee"), decimal(order, "paymentAmount")));
		return result;
	}

	private static Map<String, Object> pricing(BigDecimal original, BigDecimal discount, BigDecimal shipping, BigDecimal payment) {
		return Map.of(
				"originalAmount", original,
				"subtotalAmount", original.subtract(discount),
				"discountAmount", discount,
				"shippingFee", shipping,
				"finalAmount", payment,
				"paymentAmount", payment);
	}
}
