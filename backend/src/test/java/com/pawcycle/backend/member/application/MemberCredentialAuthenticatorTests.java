package com.pawcycle.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

class MemberCredentialAuthenticatorTests {

  @Test
  void delegatesUnauthenticatedCredentialsToTheAuthenticationManager() {
    AuthenticationManager manager = mock(AuthenticationManager.class);
    Authentication authenticated = mock(Authentication.class);
    when(manager.authenticate(org.mockito.ArgumentMatchers.any())).thenReturn(authenticated);
    MemberCredentialAuthenticator authenticator = new MemberCredentialAuthenticator(manager);

    assertThat(authenticator.authenticate("member@example.com", "presented-password"))
        .isSameAs(authenticated);

    org.mockito.ArgumentCaptor<Authentication> token =
        org.mockito.ArgumentCaptor.forClass(Authentication.class);
    verify(manager).authenticate(token.capture());
    assertThat(token.getValue().getPrincipal()).isEqualTo("member@example.com");
    assertThat(token.getValue().getCredentials()).isEqualTo("presented-password");
    assertThat(token.getValue().isAuthenticated()).isFalse();
  }
}
