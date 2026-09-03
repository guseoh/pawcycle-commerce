package com.pawcycle.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.domain.MemberRole;
import com.pawcycle.backend.member.persistence.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

class MemberAuthenticationProviderTests {
  private static final String DUMMY_PASSWORD_HASH = "dummy-password-hash";
  private static final String PRESENTED_PASSWORD = "presented-password";

  private final MemberRepository memberRepository = mock(MemberRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private MemberAuthenticationProvider provider;

  @BeforeEach
  void setUp() {
    when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_PASSWORD_HASH);
    provider = new MemberAuthenticationProvider(memberRepository, passwordEncoder);
  }

  @Test
  void unknownEmailUsesDummyHashForExactlyOneComparison() {
    when(memberRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> provider.authenticate(token("unknown@example.com", PRESENTED_PASSWORD)))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(passwordEncoder).matches(PRESENTED_PASSWORD, DUMMY_PASSWORD_HASH);
    verify(passwordEncoder).encode(anyString());
    verifyNoMoreInteractions(passwordEncoder);
  }

  @Test
  void wrongPasswordUsesTheMemberHashForExactlyOneComparison() {
    Member member = mock(Member.class);
    when(member.getPasswordHash()).thenReturn("member-password-hash");
    when(memberRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
    when(passwordEncoder.matches(PRESENTED_PASSWORD, "member-password-hash")).thenReturn(false);

    assertThatThrownBy(() -> provider.authenticate(token("member@example.com", PRESENTED_PASSWORD)))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(passwordEncoder, times(1)).matches(PRESENTED_PASSWORD, "member-password-hash");
  }

  @Test
  void successReturnsSessionSafePrincipalWithTheMemberRole() {
    Member member = mock(Member.class);
    when(member.getId()).thenReturn(7L);
    when(member.getRole()).thenReturn(MemberRole.ADMIN);
    when(member.getPasswordHash()).thenReturn("member-password-hash");
    when(memberRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(member));
    when(passwordEncoder.matches(PRESENTED_PASSWORD, "member-password-hash")).thenReturn(true);

    Authentication authenticated =
        provider.authenticate(token("admin@example.com", PRESENTED_PASSWORD));

    assertThat(authenticated.getPrincipal())
        .isEqualTo(new AuthenticatedMemberPrincipal(7L, MemberRole.ADMIN));
    assertThat(authenticated.getCredentials()).isNull();
    assertThat(authenticated.getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_ADMIN");
  }

  private Authentication token(String email, String password) {
    return UsernamePasswordAuthenticationToken.unauthenticated(email, password);
  }
}
