package com.pawcycle.backend.catalog.product.application;

import com.pawcycle.backend.catalog.engagement.application.ReviewSummaryAiClient;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ProductCommerceAiConfiguration {
  @Bean
  @ConditionalOnProperty(
      prefix = "pawcycle.ai.review-summary",
      name = "enabled",
      havingValue = "true")
  ReviewSummaryAiClient reviewSummaryClient(
      @Value("${spring.ai.openai.api-key:}") String key,
      @Value("${pawcycle.ai.review-summary.model:}") String model) {
    ChatClient client = client(key, model);
    return reviews ->
        client
            .prompt()
            .system("반려동물 상품의 공개 리뷰만 바탕으로 짧은 한국어 요약을 작성하세요. 의료나 치료 주장을 하지 마세요.")
            .user(
                reviews.stream()
                    .map(review -> "평점=" + review.rating() + ", 내용=" + review.content())
                    .collect(Collectors.joining("\n")))
            .call()
            .content();
  }

  @Bean
  @ConditionalOnMissingBean(ReviewSummaryAiClient.class)
  ReviewSummaryAiClient disabledReviewSummaryClient() {
    return reviews -> null;
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "pawcycle.ai.product-comparison",
      name = "enabled",
      havingValue = "true")
  ProductComparisonAiClient productComparisonClient(
      @Value("${spring.ai.openai.api-key:}") String key,
      @Value("${pawcycle.ai.product-comparison.model:}") String model) {
    ChatClient client = client(key, model);
    return products ->
        client
            .prompt()
            .system(
                "제공된 상품 비교 사실만 이용해 짧은 한국어 비교 설명을 작성하세요. 가격, 재고, 구매 가능 여부, 평점과 의료 주장을 바꾸거나 만들지 마세요.")
            .user(products.stream().map(Object::toString).collect(Collectors.joining("\n")))
            .call()
            .content();
  }

  @Bean
  @ConditionalOnMissingBean(ProductComparisonAiClient.class)
  ProductComparisonAiClient disabledProductComparisonClient() {
    return products -> null;
  }

  private ChatClient client(String key, String model) {
    if (key == null || key.isBlank() || model == null || model.isBlank())
      throw new IllegalStateException(
          "Product commerce AI requires an API key and model when enabled");
    OpenAiChatModel modelClient =
        OpenAiChatModel.builder()
            .options(OpenAiChatOptions.builder().apiKey(key).model(model).build())
            .build();
    return ChatClient.builder(modelClient).build();
  }
}
