package com.pawcycle.backend.member.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.member.application.EmailNormalizer;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

class ProductionAuthSmokeMemberServiceTests {

  private static final String TEST_PASSWORD_HASH = "TEST_ONLY_PASSWORD_HASH";

  private final EmailNormalizer emailNormalizer = new EmailNormalizer();
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final MemberRepository memberRepository = mock(MemberRepository.class);
  private ProductionAuthSmokeMemberService memberService;

  @BeforeEach
  void setUp() {
    memberService =
        new ProductionAuthSmokeMemberService(emailNormalizer, passwordEncoder, memberRepository);
  }

  @Test
  void normalizesEmailEncodesPasswordAndCreatesExactlyOneMember() {
    String email = "Ops-019-" + UUID.randomUUID() + "@EXAMPLE.TEST";
    String normalizedEmail = email.substring(0, email.indexOf('@')) + "@example.test";
    String password = runtimePassword();
    when(memberRepository.findByEmailForUpdate(normalizedEmail)).thenReturn(Optional.empty());
    when(passwordEncoder.encode(password)).thenReturn(TEST_PASSWORD_HASH);

    memberService.create(email, password);

    ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
    verify(memberRepository).saveAndFlush(memberCaptor.capture());
    assertThat(memberCaptor.getValue().getEmail()).isEqualTo(normalizedEmail);
    assertThat(memberCaptor.getValue().getPasswordHash()).isEqualTo(TEST_PASSWORD_HASH);
    verify(passwordEncoder).encode(password);
  }

  @Test
  void invalidEmailAndBlankPasswordFailBeforeRepositoryAccess() {
    assertThatThrownBy(() -> memberService.create("invalid-email", runtimePassword()))
        .isInstanceOf(ProductionAuthSmokeMemberCreationException.class)
        .hasMessage("Production auth smoke member creation failed.")
        .hasNoCause();

    assertThatThrownBy(() -> memberService.create(runtimeEmail(), " \t"))
        .isInstanceOf(ProductionAuthSmokeMemberCreationException.class)
        .hasNoCause();

    verifyNoInteractions(passwordEncoder, memberRepository);
  }

  @Test
  void duplicateEmailDoesNotEncodeSaveOrChangeExistingHash() {
    String email = runtimeEmail();
    String existingHash = "EXISTING_TEST_ONLY_HASH";
    Member existingMember = new Member(email, existingHash);
    when(memberRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(existingMember));

    assertThatThrownBy(() -> memberService.create(email, runtimePassword()))
        .isInstanceOf(ProductionAuthSmokeMemberCreationException.class)
        .hasNoCause();

    assertThat(existingMember.getPasswordHash()).isEqualTo(existingHash);
    verifyNoInteractions(passwordEncoder);
    verify(memberRepository, never()).saveAndFlush(any());
  }

  @Test
  void saveFailureHidesCredentialAndPersistenceDetails() {
    String email = runtimeEmail();
    String password = runtimePassword();
    when(memberRepository.findByEmailForUpdate(email)).thenReturn(Optional.empty());
    when(passwordEncoder.encode(password)).thenReturn(TEST_PASSWORD_HASH);
    when(memberRepository.saveAndFlush(any(Member.class)))
        .thenThrow(new DataAccessResourceFailureException("SENSITIVE_DATABASE_DETAIL"));

    assertThatThrownBy(() -> memberService.create(email, password))
        .isInstanceOf(ProductionAuthSmokeMemberCreationException.class)
        .hasMessage("Production auth smoke member creation failed.")
        .hasMessageNotContaining(email)
        .hasMessageNotContaining(password)
        .hasMessageNotContaining(TEST_PASSWORD_HASH)
        .hasMessageNotContaining("SENSITIVE_DATABASE_DETAIL")
        .hasNoCause();
  }

  private String runtimeEmail() {
    return "ops-019-" + UUID.randomUUID() + "@example.test";
  }

  private String runtimePassword() {
    return UUID.randomUUID().toString();
  }
}
