package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.commerce.cart.api.CartController;
import com.pawcycle.backend.commerce.cart.api.CartItemResponse;
import com.pawcycle.backend.commerce.cart.api.CartPricingResponse;
import com.pawcycle.backend.commerce.cart.api.CartResponse;
import com.pawcycle.backend.commerce.cart.application.CartApplicationService;
import com.pawcycle.backend.commerce.cart.model.CartItemView;
import com.pawcycle.backend.commerce.cart.model.CartPricingView;
import com.pawcycle.backend.commerce.cart.model.CartView;
import com.pawcycle.backend.commerce.cart.persistence.CartPersistenceAdapter;
import com.pawcycle.backend.commerce.wishlist.api.WishlistController;
import com.pawcycle.backend.commerce.wishlist.api.WishlistItemResponse;
import com.pawcycle.backend.commerce.wishlist.api.WishlistResponse;
import com.pawcycle.backend.commerce.wishlist.application.WishlistApplicationService;
import com.pawcycle.backend.commerce.wishlist.model.WishlistItemView;
import com.pawcycle.backend.commerce.wishlist.persistence.WishlistQueryRepository;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

class ShoppingStateResponseBoundaryTests {
  private static final ObjectMapper JSON = new ObjectMapper();

  static IntStream patterns() { return IntStream.range(0, 64); }

  @ParameterizedTest
  @MethodSource("patterns")
  void cartItemFactoryKeepsEveryFieldNullableDecimalScaleAndJson(int pattern) throws Exception {
    var view = new CartItemView(71L, 3, (pattern & 1) == 0 ? "SKU-한글" : null,
        (pattern & 2) == 0 ? "옵션" : null, decimal(pattern, 4), decimal(pattern, 8), decimal(pattern, 16),
        13L, (pattern & 32) == 0 ? "상품" : null, 0, false);
    var expected = FrozenShoppingStateResponses.item(view);
    assertThat(CartItemResponse.from(view)).isEqualTo(expected);
    assertThat(JSON.writeValueAsString(CartItemResponse.from(view))).isEqualTo(JSON.writeValueAsString(expected));
  }

  @ParameterizedTest
  @MethodSource("patterns")
  void sixPricingFieldsPreserveIndependentNullableValuesAndScale(int pattern) throws Exception {
    var view = new CartPricingView(decimal(pattern, 1), decimal(pattern, 2), decimal(pattern, 4),
        decimal(pattern, 8), decimal(pattern, 16), decimal(pattern, 32));
    var expected = new CartPricingResponse(view.originalAmount(), view.subtotalAmount(), view.discountAmount(),
        view.shippingFee(), view.finalAmount(), view.paymentAmount());
    assertThat(CartPricingResponse.from(view)).isEqualTo(expected);
    assertThat(JSON.writeValueAsString(CartPricingResponse.from(view))).isEqualTo(JSON.writeValueAsString(expected));
  }

  @Test
  void cartUseCaseKeepsBigDecimalReductionSixAmountsVersionOrderAndEmptyZero() throws Exception {
    var repository = mock(CartPersistenceAdapter.class);
    var service = new CartApplicationService(repository);
    var first = cartItem(71L, "12.3400");
    var second = cartItem(13L, "0.10");
    for (var view : List.of(new CartView(List.of(first, second, first), Long.MAX_VALUE), new CartView(List.of(), 0))) {
      when(repository.view(53L)).thenReturn(view);
      var result = service.get(53L);
      assertThat(result.cart()).isSameAs(view);
      var actual = CartResponse.from(result);
      var expected = FrozenShoppingStateResponses.cart(view);
      assertThat(actual).isEqualTo(expected);
      assertThat(JSON.writeValueAsString(actual)).isEqualTo(JSON.writeValueAsString(expected));
      assertThat(result.pricing().originalAmount()).isEqualTo(view.items().isEmpty() ? BigDecimal.ZERO : new BigDecimal("24.7800"));
      assertThat(result.pricing().discountAmount()).isEqualTo(BigDecimal.ZERO);
      assertThat(result.pricing().shippingFee()).isEqualTo(BigDecimal.ZERO);
      assertThatThrownBy(() -> actual.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  void responseAndViewConstructorsKeepSnapshotsNullRejectionAndNullableFields() {
    var item = cartItem(1L, "0.00");
    List<CartItemView> source = new ArrayList<>(List.of(item));
    var view = new CartView(source, 7L);
    source.clear();
    assertThat(view.items()).containsExactly(item);
    assertThatThrownBy(() -> view.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new CartView(null, 0)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new CartView(Arrays.asList((CartItemView) null), 0)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new CartResponse(null, 0, null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new CartResponse(Arrays.asList((CartItemResponse) null), 0, null)).isInstanceOf(NullPointerException.class);
    List<CartItemResponse> httpItems = new ArrayList<>(List.of(CartItemResponse.from(item)));
    var response = new CartResponse(httpItems, 7, null);
    httpItems.clear();
    assertThat(response.items()).hasSize(1);
    assertThat(response.pricing()).isNull();
  }

  @Test
  void wishlistFactoriesKeepTimestampIdentityNanosNullsDuplicatesAndImmutableOrder() throws Exception {
    Timestamp stamp = Timestamp.from(Instant.parse("2026-10-18T14:59:59.123456Z"));
    var first = new WishlistItemView(71L, "상품", stamp);
    var second = new WishlistItemView(13L, null, null);
    for (var items : List.of(List.<WishlistItemView>of(), List.of(first, second, first))) {
      var actual = WishlistResponse.from(items);
      var expected = FrozenShoppingStateResponses.wishlist(items);
      assertThat(actual).isEqualTo(expected);
      assertThat(JSON.writeValueAsString(actual)).isEqualTo(JSON.writeValueAsString(expected));
      assertThatThrownBy(() -> actual.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    assertThat(WishlistItemResponse.from(first).createdAt()).isSameAs(stamp);
    assertThat(WishlistItemResponse.from(first).createdAt().getNanos()).isEqualTo(123456000);
    assertThatThrownBy(() -> new WishlistResponse(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new WishlistResponse(Arrays.asList((WishlistItemResponse) null))).isInstanceOf(NullPointerException.class);
    List<WishlistItemResponse> mutable = new ArrayList<>(List.of(WishlistItemResponse.from(first)));
    var response = new WishlistResponse(mutable);
    mutable.clear();
    assertThat(response.items()).hasSize(1);
  }

  @Test
  void wishlistUseCaseKeepsImmutableSnapshotAndNullFailureBeforeControllerMapping() {
    var queries = mock(WishlistQueryRepository.class);
    var service = new WishlistApplicationService(null, queries, null, null, null);
    var item = new WishlistItemView(7L, "상품", null);
    List<WishlistItemView> mutable = new ArrayList<>(List.of(item));
    when(queries.findByMemberId(53L)).thenReturn(mutable);
    var result = service.list(53L);
    mutable.clear();
    assertThat(result).containsExactly(item);
    assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
    when(queries.findByMemberId(53L)).thenReturn(Arrays.asList((WishlistItemView) null));
    assertThatThrownBy(() -> service.list(53L)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> FrozenShoppingStateResponses.wishlist(Arrays.asList((WishlistItemView) null))).isInstanceOf(NullPointerException.class);
  }

  @Test
  void bothControllersPreserveMemberBindingAndFrozenWireResponse() throws Exception {
    var cart = mock(CartPersistenceAdapter.class);
    var service = new CartApplicationService(cart);
    var snapshot = new CartView(List.of(cartItem(7L, "3.30")), 19L);
    when(cart.view(53L)).thenReturn(snapshot);
    var response = new CartController(service).get(new AuthenticatedMemberPrincipal(53L));
    assertThat(response).isEqualTo(FrozenShoppingStateResponses.cart(snapshot));
    verify(cart).view(53L);
    var wishlist = mock(WishlistApplicationService.class);
    List<WishlistItemView> items = List.of(new WishlistItemView(9L, "상품", Timestamp.from(Instant.parse("2026-10-18T00:00:00.123456Z"))));
    when(wishlist.list(53L)).thenReturn(items);
    var actual = new WishlistController(wishlist).list(new AuthenticatedMemberPrincipal(53L));
    assertThat(actual).isEqualTo(FrozenShoppingStateResponses.wishlist(items));
    assertThat(JSON.writeValueAsString(actual)).isEqualTo(JSON.writeValueAsString(FrozenShoppingStateResponses.wishlist(items)));
    verify(wishlist).list(53L);
  }

  private static BigDecimal decimal(int pattern, int bit) { return (pattern & bit) == 0 ? new BigDecimal("123.4500").add(BigDecimal.valueOf(bit)) : null; }
  private static CartItemView cartItem(long id, String amount) {
    var value = new BigDecimal(amount);
    return new CartItemView(id, 1, "SKU-" + id, "sku", value, value, value, id + 100, "product", 5, true);
  }
}
