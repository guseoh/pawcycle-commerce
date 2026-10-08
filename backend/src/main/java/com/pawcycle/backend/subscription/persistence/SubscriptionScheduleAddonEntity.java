package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Feature-local persistence mapping; state transitions remain in the existing use cases. */
@Entity(name = "SubCommandAddon")
@Table(name = "subscription_schedule_addons")
@IdClass(SubscriptionScheduleAddonId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionScheduleAddonEntity {
  @Id
  @Column(name = "schedule_id")
  private long scheduleId;

  @Id
  @Column(name = "sku_id")
  private long skuId;

  private int quantity;

  @Column(name = "unit_price_krw", precision = 18, scale = 2)
  private BigDecimal unitPriceKrw;
}
