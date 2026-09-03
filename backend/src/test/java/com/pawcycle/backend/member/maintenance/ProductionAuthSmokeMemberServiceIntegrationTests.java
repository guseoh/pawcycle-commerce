package com.pawcycle.backend.member.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.member.application.EmailNormalizer;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(ProductionAuthSmokeMemberService.class)
class ProductionAuthSmokeMemberServiceIntegrationTests {

  private final ProductionAuthSmokeMemberService memberService;
  private final EmailNormalizer emailNormalizer;
  private final PasswordEncoder passwordEncoder;
  private final MemberRepository memberRepository;
  private final TransactionTemplate transactionTemplate;
  private String createdEmail;

  @Autowired
  ProductionAuthSmokeMemberServiceIntegrationTests(
      ProductionAuthSmokeMemberService memberService,
      EmailNormalizer emailNormalizer,
      PasswordEncoder passwordEncoder,
      MemberRepository memberRepository,
      PlatformTransactionManager transactionManager) {
    this.memberService = memberService;
    this.emailNormalizer = emailNormalizer;
    this.passwordEncoder = passwordEncoder;
    this.memberRepository = memberRepository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @AfterEach
  void cleanFixture() {
    if (createdEmail != null) {
      memberRepository.findByEmail(createdEmail).ifPresent(memberRepository::delete);
    }
  }

  @Test
  void createsOneMemberWithExistingEmailAndPasswordRules() {
    createdEmail = runtimeEmail();
    String password = runtimePassword();
    long before = memberRepository.count();

    memberService.create(createdEmail, password);

    Member member = memberRepository.findByEmail(createdEmail).orElseThrow();
    assertThat(memberRepository.count()).isEqualTo(before + 1);
    assertThat(passwordEncoder.matches(password, member.getPasswordHash())).isTrue();
    assertThat(member.getPasswordHash()).isNotEqualTo(password);
  }

  @Test
  void duplicateDoesNotChangeExistingMemberOrPasswordHash() {
    createdEmail = runtimeEmail();
    String firstPassword = runtimePassword();
    memberService.create(createdEmail, firstPassword);
    Member before = memberRepository.findByEmail(createdEmail).orElseThrow();
    String originalHash = before.getPasswordHash();
    long originalCount = memberRepository.count();

    assertThatThrownBy(() -> memberService.create(createdEmail, runtimePassword()))
        .isInstanceOf(ProductionAuthSmokeMemberCreationException.class);

    Member after = memberRepository.findByEmail(createdEmail).orElseThrow();
    assertThat(after.getId()).isEqualTo(before.getId());
    assertThat(after.getPasswordHash()).isEqualTo(originalHash);
    assertThat(memberRepository.count()).isEqualTo(originalCount);
  }

  @Test
  void persistenceFailureRollsBackWithoutPartialMember() {
    createdEmail = runtimeEmail();
    PasswordEncoder oversizedHashEncoder =
        new PasswordEncoder() {
          @Override
          public String encode(CharSequence rawPassword) {
            return "X".repeat(101);
          }

          @Override
          public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return false;
          }
        };
    ProductionAuthSmokeMemberService failingService =
        new ProductionAuthSmokeMemberService(
            emailNormalizer, oversizedHashEncoder, memberRepository);

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> failingService.create(createdEmail, runtimePassword())))
        .isInstanceOf(ProductionAuthSmokeMemberCreationException.class)
        .hasNoCause();

    assertThat(memberRepository.findByEmail(createdEmail)).isEmpty();
  }

  private String runtimeEmail() {
    return "ops-019-" + UUID.randomUUID() + "@example.test";
  }

  private String runtimePassword() {
    return UUID.randomUUID().toString();
  }
}
