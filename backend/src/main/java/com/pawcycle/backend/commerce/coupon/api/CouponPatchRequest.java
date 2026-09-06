package com.pawcycle.backend.commerce.coupon.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 쿠폰 PATCH에서 전송된 필드와 명시적 null을 구분하는 요청 계약이다. */
public class CouponPatchRequest {
  private String name;
  private String discountType;
  private BigDecimal discountValue;
  private BigDecimal minimumOrderAmount;
  private BigDecimal maximumDiscountAmount;
  private LocalDateTime validFrom;
  private LocalDateTime validUntil;
  private Boolean active;
  private boolean namePresent;
  private boolean discountTypePresent;
  private boolean discountValuePresent;
  private boolean minimumOrderAmountPresent;
  private boolean maximumDiscountAmountPresent;
  private boolean validFromPresent;
  private boolean validUntilPresent;
  private boolean activePresent;

  @JsonSetter("name")
  public void setName(String name) { this.namePresent = true; this.name = name; }
  @JsonSetter("discountType")
  public void setDiscountType(String discountType) { this.discountTypePresent = true; this.discountType = discountType; }
  @JsonSetter("discountValue")
  public void setDiscountValue(BigDecimal discountValue) { this.discountValuePresent = true; this.discountValue = discountValue; }
  @JsonSetter("minimumOrderAmount")
  public void setMinimumOrderAmount(BigDecimal minimumOrderAmount) { this.minimumOrderAmountPresent = true; this.minimumOrderAmount = minimumOrderAmount; }
  @JsonSetter("maximumDiscountAmount")
  public void setMaximumDiscountAmount(BigDecimal maximumDiscountAmount) { this.maximumDiscountAmountPresent = true; this.maximumDiscountAmount = maximumDiscountAmount; }
  @JsonSetter("validFrom")
  public void setValidFrom(LocalDateTime validFrom) { this.validFromPresent = true; this.validFrom = validFrom; }
  @JsonSetter("validUntil")
  public void setValidUntil(LocalDateTime validUntil) { this.validUntilPresent = true; this.validUntil = validUntil; }
  @JsonSetter("active")
  public void setActive(Boolean active) { this.activePresent = true; this.active = active; }

  public String name() { return name; }
  public String discountType() { return discountType; }
  public BigDecimal discountValue() { return discountValue; }
  public BigDecimal minimumOrderAmount() { return minimumOrderAmount; }
  public BigDecimal maximumDiscountAmount() { return maximumDiscountAmount; }
  public LocalDateTime validFrom() { return validFrom; }
  public LocalDateTime validUntil() { return validUntil; }
  public Boolean active() { return active; }
  public boolean hasName() { return namePresent; }
  public boolean hasDiscountType() { return discountTypePresent; }
  public boolean hasDiscountValue() { return discountValuePresent; }
  public boolean hasMinimumOrderAmount() { return minimumOrderAmountPresent; }
  public boolean hasMaximumDiscountAmount() { return maximumDiscountAmountPresent; }
  public boolean hasValidFrom() { return validFromPresent; }
  public boolean hasValidUntil() { return validUntilPresent; }
  public boolean hasActive() { return activePresent; }
  public boolean isEmpty() { return !namePresent && !discountTypePresent && !discountValuePresent && !minimumOrderAmountPresent && !maximumDiscountAmountPresent && !validFromPresent && !validUntilPresent && !activePresent; }
}
