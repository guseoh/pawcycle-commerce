import type { CatalogDiscovery, ProductFilters, ProductSort } from "./api.ts";
import type { InteractionContext } from "./final-product-api.ts";

export const PRODUCT_SORTS: { value: ProductSort; label: string }[] = [
  { value: "RECOMMENDED", label: "추천순" }, { value: "NEWEST", label: "최신순" },
  { value: "PRICE_ASC", label: "낮은 가격순" }, { value: "PRICE_DESC", label: "높은 가격순" },
  { value: "RATING", label: "평점순" }, { value: "REVIEW_COUNT", label: "리뷰 많은 순" },
];

export function catalogPriceRangeError({ minPrice, maxPrice }: Pick<ProductFilters, "minPrice" | "maxPrice">): string | null {
  return minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice
    ? "최소 가격은 최대 가격보다 클 수 없습니다." : null;
}

export function parseCatalogFilters(query: URLSearchParams): ProductFilters {
  const filters: ProductFilters = { page: 0, size: 12, sort: "RECOMMENDED" };
  for (const key of ["q", "petType", "category", "subcategory", "brand"] as const) {
    const value = query.get(key)?.trim();
    if (value) filters[key] = value;
  }
  filters.facet = query.getAll("facet").filter(Boolean);
  for (const key of ["minPrice", "maxPrice", "page", "size"] as const) {
    const raw = query.get(key);
    if (raw === null || !raw.trim()) continue;
    const value = Number(raw);
    if (Number.isFinite(value) && value >= 0 &&
        ((key !== "page" && key !== "size") || Number.isInteger(value)) &&
        (key !== "size" || (value > 0 && value <= 100))) filters[key] = value;
  }
  for (const key of ["subscribable", "purchasable"] as const) {
    const value = query.get(key);
    if (value === "true" || value === "false") filters[key] = value === "true";
  }
  const sort = query.get("sort");
  if (PRODUCT_SORTS.some((item) => item.value === sort)) filters.sort = sort as ProductSort;
  return filters;
}

export function catalogQuery(filters: ProductFilters): string {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value === undefined || value === "" || (key === "page" && value === 0)) return;
    if (Array.isArray(value)) value.forEach((item) => query.append(key, item));
    else query.set(key, String(value));
  });
  return query.toString();
}

export function catalogHref(filters: ProductFilters): string {
  const query = catalogQuery(filters);
  return `/products${query ? `?${query}` : ""}`;
}

export function changeCatalogFilters(filters: ProductFilters, patch: Partial<ProductFilters>): ProductFilters {
  const next = { ...filters, ...patch, page: 0 };
  if ("category" in patch && patch.category !== filters.category) {
    next.subcategory = undefined;
    next.facet = [];
  } else if ("subcategory" in patch && patch.subcategory !== filters.subcategory) next.facet = [];
  return next;
}

export function toggleCatalogFacet(filters: ProductFilters, value: string, checked: boolean): ProductFilters {
  const facets = filters.facet ?? [];
  const nextFacets = checked
    ? facets.includes(value) ? facets : [...facets, value]
    : facets.filter((item) => item !== value);
  return changeCatalogFilters(filters, { facet: nextFacets });
}

export function interactionContext(filters: ProductFilters): InteractionContext {
  const context: InteractionContext = {
    hasTextQuery: Boolean(filters.q?.trim()),
    petType: filters.petType === "DOG" || filters.petType === "CAT" ? filters.petType : undefined,
    category: filters.category,
    subcategory: filters.subcategory,
    brand: filters.brand,
    facets: filters.facet?.slice(0, 20),
    minPrice: filters.minPrice !== undefined && Number.isFinite(filters.minPrice) && filters.minPrice >= 0 ? filters.minPrice : undefined,
    maxPrice: filters.maxPrice !== undefined && Number.isFinite(filters.maxPrice) && filters.maxPrice >= 0 ? filters.maxPrice : undefined,
    sort: filters.sort,
  };
  return Object.fromEntries(Object.entries(context).filter(([, value]) => value !== undefined)) as InteractionContext;
}

export function catalogMetadata(discovery: CatalogDiscovery | null, filters: ProductFilters) {
  const category = discovery?.categories.find((item) => item.slug === filters.category);
  const children = category?.children ?? [];
  const target = filters.subcategory
    ? children.find((item) => item.slug === filters.subcategory)?.slug
    : category?.slug;
  return { children, facets: target ? discovery?.categoryFacets.find((item) => item.categorySlug === target)?.facets ?? [] : [] };
}
