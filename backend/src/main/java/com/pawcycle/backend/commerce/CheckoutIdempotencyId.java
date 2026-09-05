package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * JPA mapping for a commerce persistence record.
 */

@Embeddable
public class CheckoutIdempotencyId implements Serializable {
  @Column(name = "member_id")
  Long memberId;

  @Column(name = "idempotency_key", length = 128)
  String idempotencyKey;

  protected CheckoutIdempotencyId() {}

  public CheckoutIdempotencyId(long memberId, String idempotencyKey) {
    this.memberId = memberId;
    this.idempotencyKey = idempotencyKey;
  }

  public long getMemberId() {
    return memberId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CheckoutIdempotencyId id
        && Objects.equals(memberId, id.memberId)
        && Objects.equals(idempotencyKey, id.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(memberId, idempotencyKey);
  }
}
