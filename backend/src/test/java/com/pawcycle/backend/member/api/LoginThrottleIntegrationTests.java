package com.pawcycle.backend.member.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** R4 per-account login failure limit through the real HTTP, security and MySQL path. */
@SpringBootTest(
    properties = {
      "pawcycle.auth.login-throttle.max-failures=3",
      "pawcycle.auth.login-throttle.failure-window=PT10M",
      "pawcycle.auth.login-throttle.lock-duration=PT2S"
    })
@ActiveProfiles("test")
@Transactional
class LoginThrottleIntegrationTests {

  private static final String RATE_LIMITED_MESSAGE = "로그인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.";

  private final WebApplicationContext applicationContext;
  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final ObjectMapper objectMapper;
  private MockMvc mockMvc;

  @Autowired
  LoginThrottleIntegrationTests(
      WebApplicationContext applicationContext,
      MemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      ObjectMapper objectMapper) {
    this.applicationContext = applicationContext;
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.objectMapper = objectMapper;
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
  }

  @Test
  void lockedAccountRejectsEvenTheCorrectPasswordUntilTheLockExpires() throws Exception {
    String password = "password-" + UUID.randomUUID();
    Member member = member(password);

    for (int failure = 0; failure < 3; failure++) {
      assertThat(login(member.getEmail(), "wrong-" + failure).getResponse().getStatus())
          .isEqualTo(401);
    }
    MvcResult locked = login(member.getEmail(), password);
    assertRateLimited(locked);
    assertThat(locked.getResponse().getContentAsString()).doesNotContain(password);
    assertThat(
            locked
                .getRequest()
                .getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
        .as("no authenticated context may be stored while locked")
        .isNull();

    Thread.sleep(2_200);
    MvcResult released = login(member.getEmail(), password);
    assertThat(released.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(released).get("memberId").asLong()).isEqualTo(member.getId());
  }

  @Test
  void unknownAndExistingAccountsProduceIdenticalResponses() throws Exception {
    Member member = member("password-" + UUID.randomUUID());
    String unknown = "absent-" + UUID.randomUUID() + "@example.com";

    List<String> existing = new ArrayList<>();
    List<String> absent = new ArrayList<>();
    for (int attempt = 0; attempt < 5; attempt++) {
      existing.add(outcome(login(member.getEmail(), "wrong-password")));
      absent.add(outcome(login(unknown, "wrong-password")));
    }

    assertThat(existing)
        .containsExactly(
            "401 INVALID_CREDENTIALS 이메일 또는 비밀번호가 올바르지 않습니다.",
            "401 INVALID_CREDENTIALS 이메일 또는 비밀번호가 올바르지 않습니다.",
            "401 INVALID_CREDENTIALS 이메일 또는 비밀번호가 올바르지 않습니다.",
            "429 RATE_LIMITED " + RATE_LIMITED_MESSAGE,
            "429 RATE_LIMITED " + RATE_LIMITED_MESSAGE);
    assertThat(absent).isEqualTo(existing);
  }

  @Test
  void otherAccountsAndCaseVariantsBehaveIndependentlyOfTheLockedKey() throws Exception {
    Member target = member("password-" + UUID.randomUUID());
    String otherPassword = "password-" + UUID.randomUUID();
    Member other = member(otherPassword);
    for (int failure = 0; failure < 3; failure++) {
      login(target.getEmail(), "wrong-password");
    }

    assertRateLimited(login(target.getEmail().toUpperCase(), "wrong-password"));
    assertThat(login(other.getEmail(), otherPassword).getResponse().getStatus()).isEqualTo(200);
    assertThat(login(other.getEmail(), "wrong-password").getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void successfulLoginResetsTheFailureCount() throws Exception {
    String password = "password-" + UUID.randomUUID();
    Member member = member(password);
    login(member.getEmail(), "wrong-password");
    login(member.getEmail(), "wrong-password");
    assertThat(login(member.getEmail(), password).getResponse().getStatus()).isEqualTo(200);

    login(member.getEmail(), "wrong-password");
    login(member.getEmail(), "wrong-password");
    assertThat(login(member.getEmail(), password).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void validationFailuresDoNotConsumeTheBudget() throws Exception {
    String password = "password-" + UUID.randomUUID();
    Member member = member(password);
    for (int attempt = 0; attempt < 5; attempt++) {
      assertThat(login(member.getEmail(), "").getResponse().getStatus()).isEqualTo(400);
    }
    assertThat(login(member.getEmail(), password).getResponse().getStatus()).isEqualTo(200);
  }

  private Member member(String password) {
    return memberRepository.saveAndFlush(
        new Member("throttle-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode(password)));
  }

  private MvcResult login(String email, String password) throws Exception {
    MvcResult csrf = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    MockHttpSession session = (MockHttpSession) csrf.getRequest().getSession(false);
    return mockMvc
        .perform(
            post("/api/auth/login")
                .session(session)
                .header("X-CSRF-TOKEN", json(csrf).get("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
        .andReturn();
  }

  private void assertRateLimited(MvcResult result) throws Exception {
    assertThat(outcome(result)).isEqualTo("429 RATE_LIMITED " + RATE_LIMITED_MESSAGE);
    assertThat(json(result).get("fieldErrors").isEmpty()).isTrue();
  }

  private String outcome(MvcResult result) throws Exception {
    JsonNode body = json(result);
    return result.getResponse().getStatus()
        + " "
        + body.get("code").asText()
        + " "
        + body.get("message").asText();
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsByteArray());
  }
}
