package com.pawcycle.backend.commerce.cart.application;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.cart.api.CartItemResponse;
import com.pawcycle.backend.commerce.cart.api.CartPricingResponse;
import com.pawcycle.backend.commerce.cart.api.CartResponse;
import com.pawcycle.backend.commerce.cart.persistence.CartItemView;
import com.pawcycle.backend.commerce.cart.persistence.CartPersistenceAdapter;
import com.pawcycle.backend.commerce.cart.persistence.CartView;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartApplicationService {
  private final CartPersistenceAdapter cart;

  public CartApplicationService(CartPersistenceAdapter cart) {
    this.cart = cart;
  }

  @Transactional(readOnly = true)
  public CartResponse get(long memberId) {
    CartView view = cart.view(memberId);
    BigDecimal original =
        view.items().stream()
            .map(CartItemView::lineAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CartResponse(
        view.items().stream().map(CartApplicationService::item).toList(),
        view.version(),
        pricing(original));
  }

  @Transactional
  public void add(long memberId, long skuId, int quantity) {
    if (!cart.isPurchasable(skuId)) {
      throw new CommerceException(409, "SKU_NOT_PURCHASABLE", "구매할 수 없는 SKU입니다.");
    }
    cart.add(cart.lockForAdd(memberId), skuId, quantity);
  }

  @Transactional
  public void update(long memberId, long skuId, int quantity) {
    cart.update(cart.lockExisting(memberId), skuId, quantity);
  }

  @Transactional
  public void delete(long memberId, long skuId) {
    cart.delete(memberId, skuId);
  }

  private static CartItemResponse item(CartItemView item) {
    return new CartItemResponse(
        item.skuId(),
        item.quantity(),
        item.skuCode(),
        item.skuName(),
        item.price(),
        item.unitPrice(),
        item.lineAmount(),
        item.productId(),
        item.productName(),
        item.availableQuantity(),
        item.purchasable());
  }

  private static CartPricingResponse pricing(BigDecimal original) {
    return new CartPricingResponse(
        original, original, BigDecimal.ZERO, BigDecimal.ZERO, original, original);
  }
}
