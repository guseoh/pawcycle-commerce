package com.pawcycle.backend.recommendation;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RecommendationAiConfiguration {
  @Bean
  @ConditionalOnProperty(
      prefix = "pawcycle.recommendation.ai",
      name = "enabled",
      havingValue = "true")
  RecommendationAiClient openAiRecommendationClient(
      @Value("${spring.ai.openai.api-key:}") String apiKey,
      @Value("${pawcycle.recommendation.ai.model:}") String model,
      RecommendationMetrics metrics) {
    if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
      throw new IllegalStateException(
          "Recommendation AI requires an external API key and model when enabled");
    }
    OpenAiChatModel chatModel =
        OpenAiChatModel.builder()
            .options(OpenAiChatOptions.builder().apiKey(apiKey).model(model).build())
            .build();
    ChatClient chatClient = ChatClient.builder(chatModel).build();
    return (candidates, categories) ->
        metrics.recordAiCall(
            () -> {
              RecommendationAiResponse response =
                  chatClient
                      .prompt()
                      .system(
                          """
                          당신은 반려동물 소모품 상품 정렬 도우미입니다. 제공된 후보 상품만 productId로 선택하고,
                          각 선택 이유는 짧은 한국어 한 문장으로 작성하세요. 의료, 질병, 치료, 약품, 처방 관련 추천은 하지 마세요.
                          개인정보, 주문번호, 결제정보를 요구하거나 언급하지 마세요.
                          """)
                      .user(userPrompt(candidates, categories))
                      .call()
                      .entity(RecommendationAiResponse.class);
              return response == null || response.recommendations() == null
                  ? List.of()
                  : response.recommendations();
            });
  }

  @Bean
  @ConditionalOnMissingBean(RecommendationAiClient.class)
  RecommendationAiClient disabledRecommendationAiClient() {
    return (candidates, categories) -> List.of();
  }

  private String userPrompt(List<RecommendationCandidate> candidates, List<String> categories) {
    String products =
        candidates.stream()
            .limit(10)
            .map(
                candidate ->
                    "productId="
                        + candidate.productId()
                        + ", name="
                        + candidate.name()
                        + ", shortDescription="
                        + candidate.shortDescription()
                        + ", category="
                        + candidate.category().slug())
            .collect(java.util.stream.Collectors.joining("\n"));
    return "선호 카테고리 순서: "
        + String.join(", ", categories)
        + "\n후보 상품:\n"
        + products
        + "\n반환 형식: recommendations 배열의 productId와 reason만 반환하세요.";
  }

}
