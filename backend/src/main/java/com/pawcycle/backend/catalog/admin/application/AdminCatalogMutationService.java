package com.pawcycle.backend.catalog.admin.application;

import com.pawcycle.backend.commerce.AdminAuditService;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the transaction and audit unit for administrator catalog mutations. */
@Service
public class AdminCatalogMutationService {
  private final AdminAuditService audits;

  public AdminCatalogMutationService(AdminAuditService audits) {
    this.audits = audits;
  }

  @Transactional
  public void append(long adminId, String action, String referenceType, long referenceId) {
    audits.append(adminId, action, referenceType, referenceId);
  }

  @Transactional
  public <T> T execute(
      long adminId,
      String action,
      String referenceType,
      Supplier<T> mutation,
      ToLongFunction<T> referenceId) {
    T result = mutation.get();
    audits.append(adminId, action, referenceType, referenceId.applyAsLong(result));
    return result;
  }

  @Transactional
  public void execute(long adminId, String action, String referenceType, long referenceId, Runnable mutation) {
    mutation.run();
    audits.append(adminId, action, referenceType, referenceId);
  }
}
