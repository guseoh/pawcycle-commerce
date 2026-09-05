package com.pawcycle.backend.commerce.operations.api;

import com.pawcycle.backend.commerce.OperationsQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operations")
public class AdminOperationsController {
  private final OperationsQueryService operations;

  public AdminOperationsController(OperationsQueryService operations) {
    this.operations = operations;
  }

  @GetMapping
  public List<OperationsPendingResponse> pending() {
    return operations.pending();
  }
}
