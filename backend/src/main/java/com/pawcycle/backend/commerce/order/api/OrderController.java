package com.pawcycle.backend.commerce.order.api;

import com.pawcycle.backend.commerce.order.application.OrderApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderApplicationService orderService;

  public OrderController(OrderApplicationService orderService) {
    this.orderService = orderService;
  }

  @GetMapping
  public List<OrderSummaryResponse> list(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return orderService.orders(principal.memberId());
  }

  @GetMapping("/{orderId}")
  public OrderResponse get(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long orderId) {
    return orderService.order(principal.memberId(), orderId);
  }

  @PostMapping("/{orderId}/reorder")
  public OrderReorderResponse reorder(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long orderId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return orderService.reorder(principal.memberId(), orderId, idempotencyKey);
  }
}
