# 현재 HTTP 엔드포인트 색인

이 표는 **Spring이 실제로 등록한 HTTP 매핑을 테스트가 읽어서 만든 것**이다. 손으로 고치지 않는다. Controller 매핑이 바뀌면 `ApiEndpointIndexIntegrationTests`가 실패하므로, 아래 명령으로 다시 생성해 코드와 함께 커밋한다.

```bash
cd backend && ./gradlew test --tests '*ApiEndpointIndexIntegrationTests' -Dpawcycle.api-index.update=true
```

- 기준 profile: `test`. profile로 켜지는 측정용 Controller(`/internal/performance/**`)는 포함되지 않는다.
- "영역"은 경로 접두사로 나눈 것이다. 실제 인증·인가 규칙은 `common/security/SecurityConfig`가 원본이다.
- 요청·응답 필드는 아래 REST Docs 문서화 범위와 엔드포인트별 계약 문서(API-001~012)를 본다.

## REST Docs 문서화 범위

Spring REST Docs 테스트가 실제 요청을 보내 응답 필드를 검증하고, `backend/build/generated-snippets/<이름>/`에 요청·응답 예시와 필드 설명(`.adoc`)을 만든다. 문서에 적은 필드가 응답에서 사라지면 테스트가 실패한다. 범위는 위험이 낮은 공개 API부터 점진적으로 넓힌다.

| 스니펫 이름 | 엔드포인트 | 테스트 |
| --- | --- | --- |
| `products-list` | `GET /api/products` | `ProductApiDocumentationTests` |
| `products-detail` | `GET /api/products/{productId}` | `ProductApiDocumentationTests` |

## 엔드포인트 색인

<!-- api-endpoint-index:begin -->
총 141개

| HTTP | 경로 | 영역 | 처리 메서드 |
| --- | --- | --- | --- |
| GET | `/api/addresses` | 고객 | `MemberAddressController.list` |
| POST | `/api/addresses` | 고객 | `MemberAddressController.create` |
| DELETE | `/api/addresses/{addressId}` | 고객 | `MemberAddressController.delete` |
| PATCH | `/api/addresses/{addressId}` | 고객 | `MemberAddressController.update` |
| PUT | `/api/addresses/{addressId}/default` | 고객 | `MemberAddressController.makeDefault` |
| GET | `/api/admin/audit-logs` | 관리자 | `AdminAuditController.list` |
| GET | `/api/admin/brands` | 관리자 | `AdminCatalogController.brands` |
| POST | `/api/admin/brands` | 관리자 | `AdminCatalogController.createBrand` |
| GET | `/api/admin/brands/{brandId}` | 관리자 | `AdminCatalogController.brand` |
| PATCH | `/api/admin/brands/{brandId}` | 관리자 | `AdminCatalogController.updateBrand` |
| GET | `/api/admin/categories` | 관리자 | `AdminCatalogController.categories` |
| POST | `/api/admin/categories` | 관리자 | `AdminCatalogController.createCategory` |
| GET | `/api/admin/categories/{categoryId}` | 관리자 | `AdminCatalogController.category` |
| PATCH | `/api/admin/categories/{categoryId}` | 관리자 | `AdminCatalogController.updateCategory` |
| GET | `/api/admin/categories/{categoryId}/facets` | 관리자 | `AdminCatalogController.categoryFacets` |
| DELETE | `/api/admin/categories/{categoryId}/facets/{definitionId}` | 관리자 | `AdminCatalogController.removeCategoryFacet` |
| PUT | `/api/admin/categories/{categoryId}/facets/{definitionId}` | 관리자 | `AdminCatalogController.assignCategoryFacet` |
| GET | `/api/admin/coupons` | 관리자 | `AdminCouponController.coupons` |
| POST | `/api/admin/coupons` | 관리자 | `AdminCouponController.create` |
| PATCH | `/api/admin/coupons/{couponId}` | 관리자 | `AdminCouponController.update` |
| POST | `/api/admin/coupons/{couponId}/issues` | 관리자 | `AdminCouponController.issue` |
| POST | `/api/admin/deliveries/{id}/complete` | 관리자 | `AdminDeliveryController.complete` |
| POST | `/api/admin/deliveries/{id}/fail` | 관리자 | `AdminDeliveryController.fail` |
| POST | `/api/admin/deliveries/{id}/ship` | 관리자 | `AdminDeliveryController.ship` |
| GET | `/api/admin/facets` | 관리자 | `AdminCatalogController.facetDefinitions` |
| POST | `/api/admin/facets` | 관리자 | `AdminCatalogController.createFacetDefinition` |
| DELETE | `/api/admin/facets/{definitionId}` | 관리자 | `AdminCatalogController.deleteFacetDefinition` |
| GET | `/api/admin/facets/{definitionId}` | 관리자 | `AdminCatalogController.facetDefinition` |
| PATCH | `/api/admin/facets/{definitionId}` | 관리자 | `AdminCatalogController.updateFacetDefinition` |
| POST | `/api/admin/facets/{definitionId}/options` | 관리자 | `AdminCatalogController.createFacetOption` |
| DELETE | `/api/admin/facets/{definitionId}/options/{optionId}` | 관리자 | `AdminCatalogController.deleteFacetOption` |
| PATCH | `/api/admin/facets/{definitionId}/options/{optionId}` | 관리자 | `AdminCatalogController.updateFacetOption` |
| GET | `/api/admin/inventories` | 관리자 | `AdminInventoryController.inventories` |
| POST | `/api/admin/inventories/{skuId}/adjustments` | 관리자 | `AdminInventoryController.adjust` |
| POST | `/api/admin/members/{memberId}/membership/evaluate` | 관리자 | `AdminMembershipController.evaluate` |
| GET | `/api/admin/membership-grades` | 관리자 | `AdminMembershipController.grades` |
| POST | `/api/admin/membership-grades` | 관리자 | `AdminMembershipController.createGrade` |
| GET | `/api/admin/operations` | 관리자 | `AdminOperationsController.pending` |
| GET | `/api/admin/orders` | 관리자 | `AdminOrderController.list` |
| GET | `/api/admin/orders/{id}` | 관리자 | `AdminOrderController.get` |
| POST | `/api/admin/payments/{id}/reconcile` | 관리자 | `AdminPaymentController.reconcile` |
| POST | `/api/admin/payments/{id}/retry-billing` | 관리자 | `AdminPaymentController.retryBilling` |
| GET | `/api/admin/product-questions` | 관리자 | `AdminProductEngagementController.questions` |
| PUT | `/api/admin/product-questions/{questionId}/answer` | 관리자 | `AdminProductEngagementController.answer` |
| PATCH | `/api/admin/product-questions/{questionId}/visibility` | 관리자 | `AdminProductEngagementController.questionVisibility` |
| GET | `/api/admin/product-reviews` | 관리자 | `AdminProductEngagementController.reviews` |
| PATCH | `/api/admin/product-reviews/{reviewId}/visibility` | 관리자 | `AdminProductEngagementController.reviewVisibility` |
| GET | `/api/admin/products` | 관리자 | `AdminCatalogController.products` |
| POST | `/api/admin/products` | 관리자 | `AdminCatalogController.createProduct` |
| GET | `/api/admin/products/{productId}` | 관리자 | `AdminCatalogController.product` |
| PATCH | `/api/admin/products/{productId}` | 관리자 | `AdminCatalogController.updateProduct` |
| GET | `/api/admin/products/{productId}/detail-sections` | 관리자 | `AdminCatalogController.detailSections` |
| POST | `/api/admin/products/{productId}/detail-sections` | 관리자 | `AdminCatalogController.createDetailSection` |
| DELETE | `/api/admin/products/{productId}/detail-sections/{sectionId}` | 관리자 | `AdminCatalogController.deleteDetailSection` |
| PATCH | `/api/admin/products/{productId}/detail-sections/{sectionId}` | 관리자 | `AdminCatalogController.updateDetailSection` |
| GET | `/api/admin/products/{productId}/facet-values` | 관리자 | `AdminCatalogController.productFacetValues` |
| PUT | `/api/admin/products/{productId}/facet-values` | 관리자 | `AdminCatalogController.setProductFacetValues` |
| GET | `/api/admin/products/{productId}/images` | 관리자 | `AdminCatalogController.images` |
| POST | `/api/admin/products/{productId}/images` | 관리자 | `AdminCatalogController.createImage` |
| DELETE | `/api/admin/products/{productId}/images/{imageId}` | 관리자 | `AdminCatalogController.deleteImage` |
| PATCH | `/api/admin/products/{productId}/images/{imageId}` | 관리자 | `AdminCatalogController.updateImage` |
| GET | `/api/admin/products/{productId}/option-groups` | 관리자 | `AdminCatalogController.optionGroups` |
| POST | `/api/admin/products/{productId}/option-groups` | 관리자 | `AdminCatalogController.createOptionGroup` |
| DELETE | `/api/admin/products/{productId}/option-groups/{groupId}` | 관리자 | `AdminCatalogController.deleteOptionGroup` |
| PATCH | `/api/admin/products/{productId}/option-groups/{groupId}` | 관리자 | `AdminCatalogController.updateOptionGroup` |
| POST | `/api/admin/products/{productId}/option-groups/{groupId}/values` | 관리자 | `AdminCatalogController.createOptionValue` |
| DELETE | `/api/admin/products/{productId}/option-groups/{groupId}/values/{valueId}` | 관리자 | `AdminCatalogController.deleteOptionValue` |
| PATCH | `/api/admin/products/{productId}/option-groups/{groupId}/values/{valueId}` | 관리자 | `AdminCatalogController.updateOptionValue` |
| GET | `/api/admin/products/{productId}/skus` | 관리자 | `AdminCatalogController.skus` |
| POST | `/api/admin/products/{productId}/skus` | 관리자 | `AdminCatalogController.createSku` |
| PATCH | `/api/admin/products/{productId}/skus/{skuId}` | 관리자 | `AdminCatalogController.updateSku` |
| GET | `/api/admin/products/{productId}/skus/{skuId}/option-values` | 관리자 | `AdminCatalogController.skuOptionValues` |
| PUT | `/api/admin/products/{productId}/skus/{skuId}/option-values` | 관리자 | `AdminCatalogController.setSkuOptionValues` |
| POST | `/api/admin/refunds/{id}/process` | 관리자 | `AdminRefundController.process` |
| POST | `/api/admin/refunds/{id}/reconcile` | 관리자 | `AdminRefundController.reconcile` |
| POST | `/api/admin/refunds/{id}/retry` | 관리자 | `AdminRefundController.retry` |
| POST | `/api/admin/returns/{id}/approve` | 관리자 | `AdminReturnController.approve` |
| POST | `/api/admin/returns/{id}/receive` | 관리자 | `AdminReturnController.receive` |
| POST | `/api/admin/returns/{id}/reject` | 관리자 | `AdminReturnController.reject` |
| GET | `/api/auth/csrf` | 인증 | `AuthController.csrf` |
| POST | `/api/auth/login` | 인증 | `AuthController.login` |
| POST | `/api/auth/logout` | 인증 | `AuthController.logout` |
| GET | `/api/auth/me` | 인증 | `AuthController.me` |
| GET | `/api/cart` | 고객 | `CartController.get` |
| POST | `/api/cart/items` | 고객 | `CartController.add` |
| DELETE | `/api/cart/items/{skuId}` | 고객 | `CartController.delete` |
| PATCH | `/api/cart/items/{skuId}` | 고객 | `CartController.update` |
| GET | `/api/catalog/discovery` | 고객 | `CatalogDiscoveryController.discovery` |
| GET | `/api/categories` | 고객 | `CategoryController.categories` |
| POST | `/api/checkout` | 고객 | `CheckoutController.checkout` |
| GET | `/api/coupons` | 고객 | `MemberCouponController.list` |
| POST | `/api/interactions` | 고객 | `InteractionController.record` |
| GET | `/api/membership` | 고객 | `MembershipController.get` |
| GET | `/api/notifications` | 고객 | `NotificationController.list` |
| PATCH | `/api/notifications/read-all` | 고객 | `NotificationController.readAll` |
| PATCH | `/api/notifications/{id}/read` | 고객 | `NotificationController.read` |
| GET | `/api/orders` | 고객 | `OrderController.list` |
| GET | `/api/orders/{orderId}` | 고객 | `OrderController.get` |
| POST | `/api/orders/{orderId}/cancellations` | 고객 | `CancellationController.request` |
| POST | `/api/orders/{orderId}/reorder` | 고객 | `OrderController.reorder` |
| POST | `/api/orders/{orderId}/returns` | 고객 | `ReturnRequestController.request` |
| GET | `/api/orders/{orderId}/subscription-options` | 고객 | `RepeatCommerceController.options` |
| GET | `/api/payment-capabilities` | 고객 | `BillingController.capabilities` |
| GET | `/api/payment-methods/toss/billing` | 고객 | `BillingController.billing` |
| POST | `/api/payment-methods/toss/billing/complete` | 고객 | `BillingController.complete` |
| POST | `/api/payment-methods/toss/billing/prepare` | 고객 | `BillingController.prepare` |
| POST | `/api/payments/toss/confirm` | 고객 | `PaymentController.confirm` |
| GET | `/api/pets` | 고객 | `SubscriptionController.pets` |
| POST | `/api/pets` | 고객 | `SubscriptionController.createPet` |
| GET | `/api/pets/{petId}` | 고객 | `SubscriptionController.pet` |
| PATCH | `/api/pets/{petId}` | 고객 | `SubscriptionController.updatePet` |
| DELETE | `/api/product-questions/{questionId}` | 고객 | `ProductQuestionController.delete` |
| PATCH | `/api/product-questions/{questionId}` | 고객 | `ProductQuestionController.update` |
| GET | `/api/products` | 고객 | `ProductController.products` |
| GET | `/api/products/compare` | 고객 | `ProductComparisonController.compare` |
| GET | `/api/products/{productId}` | 고객 | `ProductController.product` |
| GET | `/api/products/{productId}/complementary` | 고객 | `ProductRecommendationController.complementary` |
| GET | `/api/products/{productId}/questions` | 고객 | `ProductQuestionController.questions` |
| POST | `/api/products/{productId}/questions` | 고객 | `ProductQuestionController.create` |
| GET | `/api/products/{productId}/related` | 고객 | `ProductRecommendationController.related` |
| GET | `/api/products/{productId}/reviews` | 고객 | `ProductReviewController.reviews` |
| POST | `/api/products/{productId}/reviews` | 고객 | `ProductReviewController.create` |
| GET | `/api/products/{productId}/reviews/me` | 고객 | `ProductReviewController.myReview` |
| GET | `/api/products/{productId}/reviews/summary` | 고객 | `ProductReviewSummaryController.summary` |
| GET | `/api/recommendations/popular` | 고객 | `ProductRecommendationController.popular` |
| GET | `/api/recommendations/products` | 고객 | `RecommendationController.products` |
| GET | `/api/recommendations/reorder-timing` | 고객 | `RepeatCommerceController.reorder` |
| GET | `/api/recommendations/trending` | 고객 | `ProductRecommendationController.trending` |
| DELETE | `/api/reviews/{reviewId}` | 고객 | `ProductReviewController.delete` |
| PATCH | `/api/reviews/{reviewId}` | 고객 | `ProductReviewController.update` |
| GET | `/api/subscription-plan-versions/{planVersionId}` | 고객 | `SubscriptionController.planVersion` |
| GET | `/api/subscription-plans` | 고객 | `SubscriptionController.plans` |
| GET | `/api/subscriptions` | 고객 | `SubscriptionController.subscriptions` |
| POST | `/api/subscriptions` | 고객 | `SubscriptionController.createSubscription` |
| GET | `/api/subscriptions/{subscriptionId}` | 고객 | `SubscriptionController.subscription` |
| POST | `/api/subscriptions/{subscriptionId}/commands/{command}` | 고객 | `SubscriptionController.command` |
| GET | `/api/subscriptions/{subscriptionId}/cycle-suggestion` | 고객 | `RepeatCommerceController.cycle` |
| PUT | `/api/subscriptions/{subscriptionId}/shipping-address` | 고객 | `MemberAddressController.updateSubscriptionShipping` |
| GET | `/api/wishlist` | 고객 | `WishlistController.list` |
| DELETE | `/api/wishlist/{productId}` | 고객 | `WishlistController.remove` |
| POST | `/api/wishlist/{productId}` | 고객 | `WishlistController.add` |
<!-- api-endpoint-index:end -->
