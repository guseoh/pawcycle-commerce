package com.pawcycle.backend.commerce.cart.model;

/** A materialized cart snapshot and the use case's pricing, without duplicate item/version fields. */
public record CartResult(CartView cart, CartPricingView pricing) {}
