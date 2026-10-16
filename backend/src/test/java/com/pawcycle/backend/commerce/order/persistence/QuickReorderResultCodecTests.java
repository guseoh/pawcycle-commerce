package com.pawcycle.backend.commerce.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.order.persistence.QuickReorderPersistenceAdapter.ReorderItem;
import com.pawcycle.backend.commerce.order.persistence.QuickReorderPersistenceAdapter.ReorderResult;
import com.pawcycle.backend.commerce.order.persistence.QuickReorderPersistenceAdapter.SkippedItem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class QuickReorderResultCodecTests {
  private final QuickReorderResultCodec codec = new QuickReorderResultCodec(new ObjectMapper());

  @Test
  void encodedBytesKeepFieldListOrderAndIntegralNumbers() {
    var value = new ReorderResult(List.of(new ReorderItem(Long.MAX_VALUE, 2), new ReorderItem(1, 3)),
        List.of(new SkippedItem(4, 5, "OUT_OF_STOCK")), 9);
    String json = "{\"addedItems\":[{\"skuId\":9223372036854775807,\"quantity\":2},{\"skuId\":1,\"quantity\":3}],\"skippedItems\":[{\"skuId\":4,\"quantity\":5,\"reason\":\"OUT_OF_STOCK\"}],\"cartVersion\":9}";
    assertThat(codec.encode(value)).isEqualTo(json);
    assertThat(codec.decode(json)).isEqualTo(value);
  }

  @Test
  void legacyNonMapAddedItemsStillBecomeZeroItemsAndExtraFieldsAreIgnored() {
    var result = codec.decode("{\"extra\":true,\"addedItems\":[null,7,\"text\",true,[]],\"skippedItems\":[],\"cartVersion\":1}");
    assertThat(result.addedItems()).containsExactly(new ReorderItem(0, 0), new ReorderItem(0, 0),
        new ReorderItem(0, 0), new ReorderItem(0, 0), new ReorderItem(0, 0));
    assertThat(result.cartVersion()).isOne();
  }

  @Test
  void legacyNumberLongValueAndIntNarrowingArePreserved() {
    var result = codec.decode("{\"addedItems\":[{\"skuId\":12.9,\"quantity\":4294967297},{\"skuId\":9223372036854775808,\"quantity\":2147483648}],\"skippedItems\":[{\"skuId\":3,\"quantity\":4,\"reason\":null}],\"cartVersion\":1.9}");
    assertThat(result.addedItems()).containsExactly(new ReorderItem(12, 1), new ReorderItem(Long.MIN_VALUE, Integer.MIN_VALUE));
    assertThat(result.skippedItems()).containsExactly(new SkippedItem(3, 4, null));
    assertThat(result.cartVersion()).isOne();
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-json", "null", "[]", "{}",
      "{\"addedItems\":[],\"skippedItems\":[],\"cartVersion\":\"1\"}",
      "{\"addedItems\":[{}],\"skippedItems\":[],\"cartVersion\":1}",
      "{\"addedItems\":[],\"skippedItems\":[null],\"cartVersion\":1}",
      "{\"addedItems\":[],\"skippedItems\":[{\"skuId\":1,\"quantity\":1,\"reason\":7}],\"cartVersion\":1}",
      "{\"addedItems\":null,\"skippedItems\":[],\"cartVersion\":1}"})
  void legacyUnreadableShapesKeepTheExactConflict(String json) {
    assertThatThrownBy(() -> codec.decode(json)).isInstanceOfSatisfying(CommerceException.class, error -> {
      assertThat(error.status()).isEqualTo(409);
      assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
      assertThat(error.getMessage()).isEqualTo("저장된 Quick Reorder 결과를 읽을 수 없습니다.");
    });
  }

  @Test
  void encodingFailurePropagatesToTheExistingAdapterStorageBoundary() {
    ObjectMapper mapper = mock(ObjectMapper.class);
    var failure = new IllegalArgumentException("test encoder failure");
    when(mapper.writeValueAsString(any())).thenThrow(failure);
    assertThatThrownBy(() -> new QuickReorderResultCodec(mapper).encode(new ReorderResult(List.of(), List.of(), 0)))
        .isSameAs(failure);
  }
}
