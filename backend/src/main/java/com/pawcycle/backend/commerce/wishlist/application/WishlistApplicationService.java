package com.pawcycle.backend.commerce.wishlist.application;

import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.WishlistItemEntity;
import com.pawcycle.backend.commerce.WishlistItemId;
import com.pawcycle.backend.commerce.WishlistItemRepository;
import com.pawcycle.backend.commerce.wishlist.api.WishlistItemResponse;
import com.pawcycle.backend.commerce.wishlist.api.WishlistResponse;
import com.pawcycle.backend.commerce.wishlist.persistence.WishlistItemView;
import com.pawcycle.backend.commerce.wishlist.persistence.WishlistQueryRepository;
import com.pawcycle.backend.member.persistence.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistApplicationService {
  private final WishlistItemRepository items;
  private final WishlistQueryRepository queries;
  private final ProductRepository products;
  private final MemberRepository members;
  private final Clock clock;

  public WishlistApplicationService(
      WishlistItemRepository items,
      WishlistQueryRepository queries,
      ProductRepository products,
      MemberRepository members,
      Clock clock) {
    this.items = items;
    this.queries = queries;
    this.products = products;
    this.members = members;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public WishlistResponse list(long memberId) {
    return new WishlistResponse(
        queries.findByMemberId(memberId).stream().map(WishlistApplicationService::response).toList());
  }

  @Transactional
  public void add(long memberId, long productId) {
    members
        .findByIdForUpdate(memberId)
        .orElseThrow(() -> new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    if (!products.existsById(productId)) {
      throw new CommerceException(404, "PRODUCT_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    WishlistItemId id = new WishlistItemId(memberId, productId);
    if (!items.existsById(id)) items.save(new WishlistItemEntity(id, LocalDateTime.now(clock)));
  }

  @Transactional
  public void remove(long memberId, long productId) {
    items.deleteById(new WishlistItemId(memberId, productId));
  }

  private static WishlistItemResponse response(WishlistItemView item) {
    return new WishlistItemResponse(item.productId(), item.productName(), item.createdAt());
  }
}
