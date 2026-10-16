package com.pawcycle.backend.catalog.admin.model;

/** Typed catalog mutation inputs, independent of HTTP and persistence contracts. */
public final class CatalogAdminCommands {
  private CatalogAdminCommands() {}

  public record BrandPatchCommand(
      String name,
      boolean namePresent,
      String slug,
      boolean slugPresent,
      String logoUrl,
      boolean logoUrlPresent,
      Boolean active,
      boolean activePresent,
      Integer displayOrder,
      boolean displayOrderPresent) {}

  public record ImageCreateCommand(
      String imageUrl, String altText, Integer displayOrder, String imageType) {}

  public record ImagePatchCommand(
      String imageUrl,
      boolean imageUrlPresent,
      String altText,
      boolean altTextPresent,
      Integer displayOrder,
      boolean displayOrderPresent,
      String imageType,
      boolean imageTypePresent) {}

  public record OptionGroupCreateCommand(String name, Integer displayOrder) {}

  public record OptionGroupPatchCommand(
      String name, boolean namePresent, Integer displayOrder, boolean displayOrderPresent) {}

  public record OptionValueCreateCommand(String value, Integer displayOrder) {}

  public record OptionValuePatchCommand(
      String value, boolean valuePresent, Integer displayOrder, boolean displayOrderPresent) {}

  public record SkuOptionValuesCommand(java.util.List<Long> optionValueIds) {}

  public record FacetDefinitionCreateCommand(String key, String name) {}

  public record FacetDefinitionPatchCommand(
      String key, boolean keyPresent, String name, boolean namePresent) {}

  public record FacetOptionCreateCommand(String value, Integer displayOrder) {}

  public record FacetOptionPatchCommand(
      String value, boolean valuePresent, Integer displayOrder, boolean displayOrderPresent) {}

  public record CategoryFacetAssignCommand(Integer displayOrder) {}

  public record ProductFacetValuesCommand(java.util.List<Long> facetOptionIds) {}

  public record DetailSectionCreateCommand(
      String title, String body, Integer displayOrder, Boolean visible) {}

  public record DetailSectionPatchCommand(
      String title,
      boolean titlePresent,
      String body,
      boolean bodyPresent,
      Integer displayOrder,
      boolean displayOrderPresent,
      Boolean visible,
      boolean visiblePresent) {}
}
