package com.pawcycle.backend.commerce.order.api;

import com.pawcycle.backend.commerce.AdminOrderQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
  private final AdminOrderQueryService orders;

  public AdminOrderController(AdminOrderQueryService orders) {
    this.orders = orders;
  }

  @GetMapping
  public List<AdminOrderResponse> list() {
    return orders.list();
  }

  @GetMapping("/{id}")
  public AdminOrderResponse get(@PathVariable long id) {
    return orders.get(id);
  }
}
