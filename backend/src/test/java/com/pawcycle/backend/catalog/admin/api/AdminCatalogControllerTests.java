package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogMutationService;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class AdminCatalogControllerTests {
  @Test
  void successfulCatalogDeleteReturnsNoContent() {
    AdminCatalogMutationService mutations = mock(AdminCatalogMutationService.class);
    AdminCatalogController controller =
        new AdminCatalogController(mock(AdminCatalogService.class), mutations);

    ResponseEntity<Void> response =
        controller.deleteImage(new AuthenticatedMemberPrincipal(7L), 11L, 13L);

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    assertThat(response.getBody()).isNull();
    verify(mutations).deleteImage(7L, 11L, 13L);
  }
}
