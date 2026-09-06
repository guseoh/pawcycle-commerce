package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.commerce.cancellation.api.CancellationController;
import com.pawcycle.backend.commerce.cancellation.api.CancellationResponse;
import com.pawcycle.backend.commerce.returning.api.ReturnResponse;
import com.pawcycle.backend.commerce.returnrequest.api.ReturnRequestController;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class CommerceResourceStatusControllerTests {
  private final AuthenticatedMemberPrincipal principal = new AuthenticatedMemberPrincipal(7L);

  @Test
  void cancellationRequestKeepsOkForCreatedOrIdempotentlyReplayedAggregate() {
    CancellationService service = mock(CancellationService.class);
    CancellationResponse expected =
        new CancellationResponse(11L, "REQUESTED", "사유", Timestamp.valueOf("2026-08-01 00:00:00"), null);
    when(service.request(7L, 19L, "사유")).thenReturn(expected);

    ResponseEntity<CancellationResponse> response =
        new CancellationController(service)
            .request(principal, 19L, new ReasonRequest("사유"));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(expected);
    assertThat(response.getHeaders().getFirst("Location")).isNull();
  }

  @Test
  void returnRequestKeepsOkForCreatedOrIdempotentlyReplayedAggregate() {
    ReturnService service = mock(ReturnService.class);
    ReturnResponse expected =
        new ReturnResponse(13L, "REQUESTED", "사유", null, null, Timestamp.valueOf("2026-08-01 00:00:00"), null, null, null);
    when(service.request(7L, 23L, "사유")).thenReturn(expected);

    ResponseEntity<ReturnResponse> response =
        new ReturnRequestController(service)
            .request(principal, 23L, new ReasonRequest("사유"));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(expected);
    assertThat(response.getHeaders().getFirst("Location")).isNull();
  }
}
