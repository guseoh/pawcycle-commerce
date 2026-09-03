package com.pawcycle.backend.member.maintenance;

import com.pawcycle.backend.member.application.EmailNormalizer;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

public class ProductionAuthSmokeMemberService {

  private final EmailNormalizer emailNormalizer;
  private final PasswordEncoder passwordEncoder;
  private final MemberRepository memberRepository;

  public ProductionAuthSmokeMemberService(
      EmailNormalizer emailNormalizer,
      PasswordEncoder passwordEncoder,
      MemberRepository memberRepository) {
    this.emailNormalizer = emailNormalizer;
    this.passwordEncoder = passwordEncoder;
    this.memberRepository = memberRepository;
  }

  @Transactional
  public void create(String email, String password) {
    try {
      String normalizedEmail = normalizeAndValidate(email, password);
      if (memberRepository.findByEmailForUpdate(normalizedEmail).isPresent()) {
        throw new ProductionAuthSmokeMemberCreationException();
      }
      String passwordHash = passwordEncoder.encode(password);
      memberRepository.saveAndFlush(new Member(normalizedEmail, passwordHash));
    } catch (ProductionAuthSmokeMemberCreationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new ProductionAuthSmokeMemberCreationException();
    }
  }

  private String normalizeAndValidate(String email, String password) {
    if (password == null || password.isBlank()) {
      throw new ProductionAuthSmokeMemberCreationException();
    }
    return emailNormalizer.normalizeEmail(email);
  }
}
