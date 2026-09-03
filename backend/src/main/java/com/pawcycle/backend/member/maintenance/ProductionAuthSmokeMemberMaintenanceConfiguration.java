package com.pawcycle.backend.member.maintenance;

import com.pawcycle.backend.member.application.EmailNormalizer;
import com.pawcycle.backend.member.persistence.MemberRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnNotWebApplication
@ConditionalOnProperty(
    prefix = "pawcycle.maintenance.create-auth-smoke-member",
    name = "enabled",
    havingValue = "true")
public class ProductionAuthSmokeMemberMaintenanceConfiguration {

  @Bean
  ProductionAuthSmokeMemberService productionAuthSmokeMemberService(
      EmailNormalizer emailNormalizer,
      PasswordEncoder passwordEncoder,
      MemberRepository memberRepository) {
    return new ProductionAuthSmokeMemberService(emailNormalizer, passwordEncoder, memberRepository);
  }

  @Bean
  ApplicationRunner productionAuthSmokeMemberRunner(
      ProductionAuthSmokeMemberService memberService) {
    return new ProductionAuthSmokeMemberCommand(memberService, System.in, System.out);
  }
}
