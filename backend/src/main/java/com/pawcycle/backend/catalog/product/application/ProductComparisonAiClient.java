package com.pawcycle.backend.catalog.product.application;

import java.util.List;

public interface ProductComparisonAiClient {
	String compare(List<ProductComparisonFacts> products);
}
