package com.pawcycle.backend.catalog.product.diagnostics;

import org.hibernate.SessionEventListener;

/** Hibernate creates one instance per Session before transaction begin/acquisition. */
public class DiscoverySessionEventListener implements SessionEventListener {
  private final transient DiscoveryLifecycleDiagnostics.Scope scope =
      DiscoveryLifecycleDiagnostics.currentScope();

  @Override public void jdbcConnectionAcquisitionStart() { if (scope != null) scope.acquisitionStart(); }
  @Override public void jdbcConnectionAcquisitionEnd() { if (scope != null) scope.acquisitionEnd(); }
  @Override public void jdbcConnectionReleaseStart() { if (scope != null) scope.releaseStart(); }
  @Override public void jdbcConnectionReleaseEnd() { if (scope != null) scope.releaseEnd(); }
}
