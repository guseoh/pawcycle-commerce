package com.pawcycle.backend.recommendation;

import java.util.Map;

record RecommendationMemberSignals(
    Map<Long, Integer> purchases,
    Map<Long, Integer> wishlists,
    Map<Long, Integer> clicks,
    Map<Long, Integer> views,
    Map<String, Integer> subscriptionCategories,
    Map<String, Integer> subscriptionBrands,
    Map<String, Integer> subscriptionFacets,
    Map<String, Integer> purchaseCategories,
    Map<String, Integer> purchaseBrands,
    Map<String, Integer> purchaseFacets,
    Map<String, Integer> wishlistCategories,
    Map<String, Integer> wishlistBrands,
    Map<String, Integer> wishlistFacets,
    Map<String, Integer> filterCategories,
    Map<String, Integer> filterBrands,
    Map<String, Integer> filterFacets) {}
