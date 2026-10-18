package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.cart.api.CartItemResponse;
import com.pawcycle.backend.commerce.cart.api.CartPricingResponse;
import com.pawcycle.backend.commerce.cart.api.CartResponse;
import com.pawcycle.backend.commerce.cart.model.CartItemView;
import com.pawcycle.backend.commerce.cart.model.CartView;
import com.pawcycle.backend.commerce.wishlist.api.WishlistItemResponse;
import com.pawcycle.backend.commerce.wishlist.api.WishlistResponse;
import com.pawcycle.backend.commerce.wishlist.model.WishlistItemView;
import java.math.BigDecimal;
import java.util.List;

/** Frozen application response constructor arguments from main efe38e9e. */
final class FrozenShoppingStateResponses {
  private FrozenShoppingStateResponses() {}

  static CartResponse cart(CartView view) {
    BigDecimal original = view.items().stream().map(CartItemView::lineAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CartResponse(view.items().stream().map(FrozenShoppingStateResponses::item).toList(),
        view.version(), new CartPricingResponse(original, original, BigDecimal.ZERO, BigDecimal.ZERO, original, original));
  }

  static CartItemResponse item(CartItemView item) {
    return new CartItemResponse(item.skuId(), item.quantity(), item.skuCode(), item.skuName(),
        item.price(), item.unitPrice(), item.lineAmount(), item.productId(), item.productName(),
        item.availableQuantity(), item.purchasable());
  }

  static WishlistResponse wishlist(List<WishlistItemView> items) {
    return new WishlistResponse(items.stream().map(item ->
        new WishlistItemResponse(item.productId(), item.productName(), item.createdAt())).toList());
  }
}
