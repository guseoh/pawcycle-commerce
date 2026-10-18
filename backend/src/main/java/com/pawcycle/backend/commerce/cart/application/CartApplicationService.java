package com.pawcycle.backend.commerce.cart.application;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.cart.model.CartItemView;
import com.pawcycle.backend.commerce.cart.persistence.CartPersistenceAdapter;
import com.pawcycle.backend.commerce.cart.model.CartView;
import com.pawcycle.backend.commerce.cart.model.CartPricingView;
import com.pawcycle.backend.commerce.cart.model.CartResult;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartApplicationService {
  private final CartPersistenceAdapter cart;

  public CartApplicationService(CartPersistenceAdapter cart) {
    this.cart = cart;
  }

  @Transactional(readOnly = true)
  public CartResult get(long memberId) {
    CartView view = cart.view(memberId);
    BigDecimal original =
        view.items().stream()
            .map(CartItemView::lineAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CartResult(view, pricing(original));
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

  private static CartPricingView pricing(BigDecimal original) {
    return new CartPricingView(
        original, original, BigDecimal.ZERO, BigDecimal.ZERO, original, original);
  }
}
