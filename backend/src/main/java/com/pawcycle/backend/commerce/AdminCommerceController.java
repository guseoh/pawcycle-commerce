package com.pawcycle.backend.commerce;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin catalog, membership and operations adapter; transaction/audit ownership is in services. */
@RestController @RequestMapping("/api/admin")
class AdminCommerceController {
	private final CommerceService commerce; private final AdminAuditService audits; private final DeliveryService deliveries; private final ReturnService returns; private final RefundService refunds; private final PaymentReconciliationService payments; private final SubscriptionBillingService billing; private final AdminOrderQueryService orders; private final OperationsQueryService operations;
	AdminCommerceController(CommerceService commerce,AdminAuditService audits,DeliveryService deliveries,ReturnService returns,RefundService refunds,PaymentReconciliationService payments,SubscriptionBillingService billing,AdminOrderQueryService orders,OperationsQueryService operations){this.commerce=commerce;this.audits=audits;this.deliveries=deliveries;this.returns=returns;this.refunds=refunds;this.payments=payments;this.billing=billing;this.orders=orders;this.operations=operations;}
	@GetMapping("/inventories") List<Map<String,Object>> inventories(){return commerce.inventories();}
	@PostMapping("/inventories/{skuId}/adjustments") ResponseEntity<Void> adjust(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long skuId,@Valid @RequestBody CommerceRequests.Adjustment r){commerce.adjustInventory(skuId,r.delta());audits.append(p.memberId(),"INVENTORY_ADJUST","SKU",skuId);return ResponseEntity.noContent().build();}
	@GetMapping("/coupons") List<Map<String,Object>> coupons(){return commerce.coupons();}
	@PostMapping("/coupons") ResponseEntity<Map<String,Object>> createCoupon(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@Valid @RequestBody CommerceRequests.Coupon request){long id=commerce.createCoupon(request.legacyPayload());audits.append(p.memberId(),"COUPON_CREATE","COUPON",id);return ResponseEntity.created(URI.create("/api/admin/coupons/"+id)).body(Map.of("couponId",id));}
	@PatchMapping("/coupons/{couponId}") ResponseEntity<Void> patchCoupon(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long couponId,@Valid @RequestBody CommerceRequests.Coupon request){commerce.updateCoupon(couponId,request.legacyPayload());audits.append(p.memberId(),"COUPON_UPDATE","COUPON",couponId);return ResponseEntity.noContent().build();}
	@PostMapping("/coupons/{couponId}/issues") ResponseEntity<Void> issueCoupon(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long couponId,@Valid @RequestBody CommerceRequests.CouponIssue request){commerce.issueCoupon(couponId,request.memberId());audits.append(p.memberId(),"COUPON_ISSUE","COUPON",couponId);return ResponseEntity.noContent().build();}
	@GetMapping("/membership-grades") List<Map<String,Object>> grades(){return commerce.membershipGrades();}
	@PostMapping("/membership-grades") ResponseEntity<Map<String,Object>> createGrade(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@Valid @RequestBody CommerceRequests.MembershipGrade request){long id=commerce.createMembershipGrade(request.legacyPayload());audits.append(p.memberId(),"MEMBERSHIP_GRADE_CREATE","MEMBERSHIP_GRADE",id);return ResponseEntity.created(URI.create("/api/admin/membership-grades/"+id)).body(Map.of("gradeId",id));}
	@PostMapping("/members/{memberId}/membership/evaluate") ResponseEntity<Void> evaluate(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long memberId){commerce.evaluateMembership(memberId);audits.append(p.memberId(),"MEMBERSHIP_EVALUATE","MEMBER",memberId);return ResponseEntity.noContent().build();}
	@PostMapping("/deliveries/{id}/ship") Map<String,Object> ship(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id,@Valid @RequestBody CommerceRequests.Ship r){return deliveries.ship(p.memberId(),id,r.carrierCode(),r.trackingNumber());}
	@PostMapping("/deliveries/{id}/complete") Map<String,Object> complete(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return deliveries.complete(p.memberId(),id);}
	@PostMapping("/deliveries/{id}/fail") Map<String,Object> fail(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id,@Valid @RequestBody CommerceRequests.Reason r){return deliveries.fail(p.memberId(),id,r.reason());}
	@PostMapping("/returns/{id}/approve") Map<String,Object> approve(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return returns.approve(p.memberId(),id);}
	@PostMapping("/returns/{id}/reject") Map<String,Object> reject(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id,@Valid @RequestBody CommerceRequests.Reason r){return returns.reject(p.memberId(),id,r.reason());}
	@PostMapping("/returns/{id}/receive") Map<String,Object> receive(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id,@Valid @RequestBody CommerceRequests.Receive r){return returns.receive(p.memberId(),id,r.restock());}
	@PostMapping("/refunds/{id}/process") Map<String,Object> process(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return refunds.process(id,p.memberId());}
	@PostMapping("/refunds/{id}/retry") Map<String,Object> retry(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return refunds.retry(id,p.memberId());}
	@PostMapping("/refunds/{id}/reconcile") Map<String,Object> reconcileRefund(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return refunds.reconcile(id,p.memberId());}
	@PostMapping("/payments/{id}/reconcile") Map<String,Object> reconcilePayment(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){return payments.reconcile(id,p.memberId());}
	@PostMapping("/payments/{id}/retry-billing") Map<String,Object> retryBilling(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long id){long next=billing.retryHeldBilling(id,p.memberId());return Map.of("paymentId",id,"nextPaymentId",next,"status",next==0?"HELD":"READY");}
	@GetMapping("/orders") List<Map<String,Object>> orders(){return orders.list();}
	@GetMapping("/orders/{id}") Map<String,Object> order(@PathVariable long id){return orders.get(id);}
	@GetMapping("/operations") List<Map<String,Object>> operations(){return operations.pending();}
	@GetMapping("/audit-logs") List<Map<String,Object>> auditLogs(){return audits.list();}
}
