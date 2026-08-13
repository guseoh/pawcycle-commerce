package com.pawcycle.backend.commerce;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CommerceFinalController {
	private final CancellationService cancellations;
	private final ReturnService returns;
	private final DeliveryService deliveries;
	private final RefundService refunds;
	private final PaymentReconciliationService payments;
	private final NotificationService notifications;
	private final OperationsQueryService operations;
	private final AdminAuditService audits;
	private final TossPaymentAdapter provider;
	private final JdbcTemplate jdbc;
	private final SubscriptionBillingService billingService;

	public CommerceFinalController(CancellationService cancellations,ReturnService returns,DeliveryService deliveries,RefundService refunds,PaymentReconciliationService payments,NotificationService notifications,OperationsQueryService operations,AdminAuditService audits,TossPaymentAdapter provider,JdbcTemplate jdbc,SubscriptionBillingService billingService) {
		this.cancellations=cancellations;
		this.returns=returns;
		this.deliveries=deliveries;
		this.refunds=refunds;
		this.payments=payments;
		this.notifications=notifications;
		this.operations=operations;
		this.audits=audits;
		this.provider=provider;
		this.jdbc=jdbc;
		this.billingService=billingService;
	}

	@GetMapping("/payment-capabilities") Map<String,Object> capabilities(){return Map.of("paymentCapabilities",provider.isConfigured()?"SANDBOX":"UNAVAILABLE");}
	@GetMapping("/payment-methods/toss/billing") Map<String,Object> billing(@AuthenticationPrincipal AuthenticatedMemberPrincipal p){Integer active=jdbc.queryForObject("SELECT COUNT(*) FROM billing_payment_methods WHERE member_id=? AND status='ACTIVE'",Integer.class,p.memberId());return Map.of("provider","TOSS","configured",provider.isConfigured(),"registered",active!=null&&active>0);}
	@PostMapping("/orders/{orderId}/cancellations") Map<String,Object> cancellation(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long orderId,@Valid @RequestBody ReasonRequest request){return cancellations.request(p.memberId(),orderId,request.reason());}
	@PostMapping("/orders/{orderId}/returns") Map<String,Object> returnRequest(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long orderId,@Valid @RequestBody ReasonRequest request){return returns.request(p.memberId(),orderId,request.reason());}
	@GetMapping("/notifications") List<Map<String,Object>> notifications(@AuthenticationPrincipal AuthenticatedMemberPrincipal p){return notifications.list(p.memberId());}
	@PatchMapping("/notifications/{id}/read") ResponseEntity<Void> read(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){notifications.read(p.memberId(),id);return ResponseEntity.noContent().build();}
	@PatchMapping("/notifications/read-all") ResponseEntity<Void> readAll(@AuthenticationPrincipal AuthenticatedMemberPrincipal p){notifications.readAll(p.memberId());return ResponseEntity.noContent().build();}
	@PostMapping("/admin/deliveries/{id}/ship") Map<String,Object> ship(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id,@Valid @RequestBody ShipRequest r){return deliveries.ship(p.memberId(),id,r.carrierCode(),r.trackingNumber());}
	@PostMapping("/admin/deliveries/{id}/complete") Map<String,Object> complete(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return deliveries.complete(p.memberId(),id);}
	@PostMapping("/admin/deliveries/{id}/fail") Map<String,Object> fail(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id,@Valid @RequestBody ReasonRequest r){return deliveries.fail(p.memberId(),id,r.reason());}
	@PostMapping("/admin/returns/{id}/approve") Map<String,Object> approve(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return returns.approve(p.memberId(),id);}
	@PostMapping("/admin/returns/{id}/reject") Map<String,Object> reject(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id,@Valid @RequestBody ReasonRequest r){return returns.reject(p.memberId(),id,r.reason());}
	@PostMapping("/admin/returns/{id}/receive") Map<String,Object> receive(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id,@Valid @RequestBody ReceiveRequest r){return returns.receive(p.memberId(),id,r.restock());}
	@PostMapping("/admin/refunds/{id}/process") Map<String,Object> process(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return refunds.process(id,p.memberId());}
	@PostMapping("/admin/refunds/{id}/retry") Map<String,Object> retry(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return refunds.retry(id,p.memberId());}
	@PostMapping("/admin/refunds/{id}/reconcile") Map<String,Object> reconcileRefund(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return refunds.reconcile(id,p.memberId());}
	@PostMapping("/admin/payments/{id}/reconcile") Map<String,Object> reconcilePayment(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return payments.reconcile(id,p.memberId());}
	@PostMapping("/admin/payments/{id}/retry-billing") Map<String,Object> retryBilling(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){long next=billingService.retryHeldBilling(id,p.memberId());return Map.of("paymentId",id,"nextPaymentId",next,"status",next==0?"HELD":"READY");}
	@GetMapping("/admin/orders") List<Map<String,Object>> orders(){return jdbc.queryForList("SELECT id AS orderId,order_number AS orderNumber,member_id AS memberId,status,payment_amount AS paymentAmount,created_at AS createdAt FROM orders ORDER BY id DESC");}
	@GetMapping("/admin/orders/{id}") Map<String,Object> order(@PathVariable long id){var rows=jdbc.queryForList("SELECT id AS orderId,order_number AS orderNumber,member_id AS memberId,status,payment_amount AS paymentAmount,created_at AS createdAt FROM orders WHERE id=?",id);if(rows.isEmpty())throw new CommerceException(404,"ORDER_NOT_FOUND","요청한 리소스를 찾을 수 없습니다.");return rows.getFirst();}
	@GetMapping("/admin/operations") List<Map<String,Object>> operations(){return operations.pending();}
	@GetMapping("/admin/audit-logs") List<Map<String,Object>> audits(){return audits.list();}

	public record ReasonRequest(@NotBlank String reason) {}
	public record ShipRequest(@NotBlank String carrierCode,@NotBlank String trackingNumber) {}
	public record ReceiveRequest(@NotNull Boolean restock) {}
}
