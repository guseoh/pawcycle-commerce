package com.pawcycle.backend.commerce.cart.persistence;

import com.pawcycle.backend.commerce.CartEntity;
import com.pawcycle.backend.commerce.CartItemEntity;
import com.pawcycle.backend.commerce.CartItemId;
import com.pawcycle.backend.commerce.CartItemRepository;
import com.pawcycle.backend.commerce.CartRepository;
import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class CartPersistenceAdapter {
  private final CartRepository carts;
  private final CartItemRepository items;
  private final MemberRepository members;
  private final CartQueryRepository queries;
  private final Clock clock;

  public CartPersistenceAdapter(
      CartRepository carts,
      CartItemRepository items,
      MemberRepository members,
      CartQueryRepository queries,
      Clock clock) {
    this.carts = carts;
    this.items = items;
    this.members = members;
    this.queries = queries;
    this.clock = clock;
  }

  public CartView view(long memberId) {
    return queries.find(memberId);
  }

  public CartLock lockForAdd(long memberId) {
    Member member =
        members
            .findByIdForUpdate(memberId)
            .orElseThrow(
                () -> new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    CartEntity cart =
        carts.findByMemberIdForUpdate(memberId).orElseGet(() -> carts.saveAndFlush(new CartEntity(member.getId(), now())));
    return new CartLock(cart.getId(), cart.getVersion());
  }

  public CartLock lockExisting(long memberId) {
    members
        .findByIdForUpdate(memberId)
        .orElseThrow(
            () -> new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    CartEntity cart =
        carts
            .findByMemberIdForUpdate(memberId)
            .orElseThrow(
                () -> new CommerceException(404, "CART_ITEM_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    return new CartLock(cart.getId(), cart.getVersion());
  }

  public void add(CartLock lock, long skuId, int quantity) {
    CartItemId itemId = new CartItemId(lock.id(), skuId);
    CartItemEntity item =
        items.findById(itemId).orElseGet(() -> new CartItemEntity(itemId, 0));
    item.increase(quantity);
    items.save(item);
    increment(lock.id());
  }

  public void update(CartLock lock, long skuId, int quantity) {
    CartItemEntity item =
        items
            .findById(new CartItemId(lock.id(), skuId))
            .orElseThrow(
                () -> new CommerceException(404, "CART_ITEM_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    if (item.getQuantity() != quantity) {
      item.updateQuantity(quantity);
      items.save(item);
      increment(lock.id());
    }
  }

  public void delete(long memberId, long skuId) {
    members
        .findByIdForUpdate(memberId)
        .orElseThrow(
            () -> new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    CartEntity cart = carts.findByMemberIdForUpdate(memberId).orElse(null);
    if (cart == null) return;
    CartLock lock = new CartLock(cart.getId(), cart.getVersion());
    CartItemId itemId = new CartItemId(lock.id(), skuId);
    if (items.existsById(itemId)) {
      items.deleteById(itemId);
      increment(lock.id());
    }
  }

  public boolean isPurchasable(long skuId) {
    return queries.isPurchasable(skuId);
  }

  private void increment(long cartId) {
    carts
        .findById(cartId)
        .ifPresent(
            cart -> {
              cart.incrementVersion(now());
              carts.save(cart);
            });
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public record CartLock(long id, long version) {}
}
