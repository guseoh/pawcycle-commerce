export function formatComparisonFacets(facets: readonly string[]): string {
  const values = facets
    .map((facet) => {
      const separator = facet.indexOf(":");
      return (separator >= 0 ? facet.slice(separator + 1) : facet).trim();
    })
    .filter((value) => value.length > 0);

  const uniqueValues = [...new Set(values)];
  return uniqueValues.length > 0 ? uniqueValues.join(" · ") : "-";
}
