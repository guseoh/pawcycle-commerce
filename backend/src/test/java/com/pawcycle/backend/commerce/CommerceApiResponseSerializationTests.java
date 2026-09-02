package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CommerceApiResponseSerializationTests {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void fixedResponseTypesPreserveExistingJsonFieldNames() throws Exception {
    assertThat(json(new PaymentCapabilitiesResponse("SANDBOX")))
        .isEqualTo("{\"paymentCapabilities\":\"SANDBOX\"}");
    assertThat(json(new BillingMethodResponse("TOSS", true, false)))
        .isEqualTo("{\"provider\":\"TOSS\",\"configured\":true,\"registered\":false}");
    assertThat(json(new BillingPreparationResponse("prepared")))
        .isEqualTo("{\"prepareToken\":\"prepared\"}");
    assertThat(json(new CouponCreatedResponse(11L))).isEqualTo("{\"couponId\":11}");
    assertThat(json(new MembershipGradeCreatedResponse(12L))).isEqualTo("{\"gradeId\":12}");
    assertThat(json(new AddressCreatedResponse(13L))).isEqualTo("{\"addressId\":13}");
    assertThat(json(new BillingRetryResponse(14L, 15L, "READY")))
        .isEqualTo("{\"paymentId\":14,\"nextPaymentId\":15,\"status\":\"READY\"}");
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }
}
